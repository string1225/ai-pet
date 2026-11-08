#!/usr/bin/env python3
"""Deploy the feedback service through an existing SSH alias; credentials stay outside Git."""
import argparse
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import tarfile
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[1]

REMOTE = r'''
import json, os, pathlib, secrets, shutil, subprocess, sys, tarfile, time
from urllib.request import urlopen
incoming=json.load(sys.stdin)
base=pathlib.Path('/home/junte/ai-pet-feedback')
private=pathlib.Path('/home/junte/.config/ai-pet-feedback')
data=pathlib.Path('/home/junte/.local/share/ai-pet-feedback')
for directory in (base,private,data,pathlib.Path('/home/junte/.codex')):
    directory.mkdir(parents=True,exist_ok=True)
private.chmod(0o700); data.chmod(0o700)
config_path=private/'config.json'
config=json.loads(config_path.read_text()) if config_path.exists() else {}
for key in ('project_token','project_session_url'):
    if incoming.get(key): config[key]=incoming[key]
    if not config.get(key): raise SystemExit('Missing private '+key)
config.setdefault('pairing_code',secrets.token_hex(6).upper())
config.update(database=str(data/'feedback.sqlite3'),repository=str(base/'repository'),
              public_base_url='https://home.sunny-string.cn/ai-pet-feedback',listen='127.0.0.1:3057',
              planner_cli='/usr/local/bin/codex',planner_timeout=180)
stamp=time.strftime('%Y%m%dT%H%M%S',time.gmtime())
backup=base/'backups'/stamp; backup.mkdir(parents=True,exist_ok=True)
if config_path.exists(): shutil.copy2(config_path,backup/'config.json'); (backup/'config.json').chmod(0o600)
if (base/'backend').exists(): shutil.copytree(base/'backend',backup/'backend')
def unpack():
    with tarfile.open(incoming['archive']) as archive:
        for member in archive.getmembers():
            if not member.name.startswith('backend/') or '..' in pathlib.PurePosixPath(member.name).parts or not member.isfile():
                raise RuntimeError('Invalid service archive')
            target=base/member.name; target.parent.mkdir(parents=True,exist_ok=True)
            with archive.extractfile(member) as source, target.open('wb') as output: shutil.copyfileobj(source,output)
repository=base/'repository'
previous_ref=subprocess.check_output(['git','-C',str(repository),'rev-parse','HEAD'],text=True).strip() if (repository/'.git').exists() else None
unit=pathlib.Path('/etc/systemd/system/ai-pet-feedback.service')
nginx=pathlib.Path('/etc/nginx/conf.d/sunny-string.conf')
snippet=pathlib.Path('/etc/nginx/snippets/ai-pet-feedback-location.conf')
def privileged_read(path):
    result=subprocess.run(['sudo','-n','cat',str(path)],capture_output=True)
    return result.stdout if result.returncode==0 else None
old={str(path):privileged_read(path) for path in (unit,nginx,snippet)}
for name,content in old.items():
    if content is not None: (backup/pathlib.Path(name).name).write_bytes(content)
nginx_content=old[str(nginx)]
if nginx_content is None: raise SystemExit('Existing HTTPS nginx configuration is unavailable')
include=b'    include /etc/nginx/snippets/ai-pet-feedback-location.conf;\n'
if include.strip() not in nginx_content:
    anchor=b'    include /etc/nginx/snippets/github-release-location.conf;\n'
    if anchor not in nginx_content: raise SystemExit('Existing HTTPS location anchor not found')
    nginx_content=nginx_content.replace(anchor,anchor+include,1)
staged=base/'feedback-location.conf'
staged.write_text('location = /ai-pet-feedback { return 308 /ai-pet-feedback/; }\nlocation ^~ /ai-pet-feedback/ {\n    client_max_body_size 64k;\n    proxy_pass http://127.0.0.1:3057/;\n    proxy_set_header Host $host;\n    proxy_set_header X-Real-IP $remote_addr;\n    proxy_read_timeout 35s;\n}\n')
staged_nginx=base/'sunny-string.staged.conf'; staged_nginx.write_bytes(nginx_content)
try:
    unpack()
    if not repository.exists():
        subprocess.run(['git','clone','--quiet','https://github.com/string1225/ai-pet.git',str(repository)],check=True)
    subprocess.run(['git','-C',str(repository),'fetch','--quiet','origin',incoming['ref']],check=True)
    subprocess.run(['git','-C',str(repository),'checkout','--quiet','--detach','FETCH_HEAD'],check=True)
    staged_config=private/'config.next.json'
    fd=os.open(staged_config,os.O_CREAT|os.O_WRONLY|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as output: json.dump(config,output)
    os.replace(staged_config,config_path); config_path.chmod(0o600)
    for source,target in ((base/'backend/ai-pet-feedback.service',unit),(staged,snippet),(staged_nginx,nginx)):
        subprocess.run(['sudo','-n','install','-m','644',str(source),str(target)],check=True,capture_output=True)
    subprocess.run(['sudo','-n','nginx','-t'],check=True,capture_output=True)
    subprocess.run(['sudo','-n','systemctl','daemon-reload'],check=True)
    subprocess.run(['sudo','-n','systemctl','enable','ai-pet-feedback.service'],check=True,capture_output=True)
    subprocess.run(['sudo','-n','systemctl','restart','ai-pet-feedback.service'],check=True)
    healthy=False
    for attempt in range(20):
        try:
            with urlopen('http://127.0.0.1:3057/health',timeout=2) as response:
                healthy=json.load(response).get('ok') is True
            if healthy: break
        except OSError: pass
        time.sleep(.5)
    if not healthy: raise RuntimeError('Feedback health check failed')
    subprocess.run(['sudo','-n','systemctl','reload','nginx'],check=True)
except Exception:
    if (backup/'config.json').exists():
        shutil.copy2(backup/'config.json',config_path); config_path.chmod(0o600)
    elif config_path.exists():
        config_path.unlink()
    if previous_ref:
        subprocess.run(['git','-C',str(repository),'checkout','--quiet','--detach',previous_ref])
    if old[str(unit)] is None:
        subprocess.run(['sudo','-n','systemctl','stop','ai-pet-feedback.service'],capture_output=True)
        subprocess.run(['sudo','-n','systemctl','disable','ai-pet-feedback.service'],capture_output=True)
    for name,content in old.items():
        if content is not None:
            restored=backup/pathlib.Path(name).name
            subprocess.run(['sudo','-n','install','-m','644',str(restored),name],check=True)
        else:
            subprocess.run(['sudo','-n','rm','-f','--',name],check=True)
    subprocess.run(['sudo','-n','systemctl','daemon-reload'])
    if (backup/'backend').exists():
        shutil.copytree(backup/'backend',base/'backend',dirs_exist_ok=True)
        if old[str(unit)] is not None: subprocess.run(['sudo','-n','systemctl','restart','ai-pet-feedback.service'])
    subprocess.run(['sudo','-n','nginx','-t'],check=True,capture_output=True)
    subprocess.run(['sudo','-n','systemctl','reload','nginx'])
    raise SystemExit('Deployment failed; existing configuration restored, inspect private service logs') from None
pathlib.Path(incoming['archive']).unlink()
print(json.dumps({'health':'ok','pairingCode':config['pairing_code'],'backup':str(backup)}))
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="aliyun-139")
    parser.add_argument("--ref", required=True, help="Pushed Git revision to inspect during planning")
    args = parser.parse_args()
    remote_archive = "/tmp/ai-pet-feedback-" + uuid.uuid4().hex + ".tar.gz"
    with tempfile.TemporaryDirectory(prefix="ai-pet-deploy-") as directory:
        archive = Path(directory) / "backend.tar.gz"
        with tarfile.open(archive, "w:gz") as output:
            for path in (ROOT / "backend").rglob("*"):
                if path.is_file() and "__pycache__" not in path.parts and "tests" not in path.parts:
                    output.add(path, arcname=path.relative_to(ROOT).as_posix())
        subprocess.run(["scp", "-q", str(archive), args.host + ":" + remote_archive], check=True)
        configuration = {"archive": remote_archive, "ref": args.ref,
                         "project_token": os.environ.get("AI_PET_PROJECT_TOKEN", ""),
                         "project_session_url": os.environ.get("AI_PET_PROJECT_SESSION_URL", "")}
        subprocess.run(["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", args.host,
                        "python3 -c " + shlex.quote(REMOTE)], input=json.dumps(configuration), text=True, check=True)


if __name__ == "__main__":
    main()
