package com.string.aipet.model;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

/** Shared prices and effects for the gold pet wallet and green family gift queue. */
public final class RewardCatalog {
    public static final class Item {
        public final String id, title, description;
        public final int cost;
        public final boolean decoration;
        private Item(String id,String title,String description,int cost,boolean decoration){
            this.id=id;this.title=title;this.description=description;this.cost=cost;this.decoration=decoration;
        }
    }
    private static final List<Item> ITEMS=Collections.unmodifiableList(Arrays.asList(
        new Item("star_garland","星星挂饰","把金色小星星挂在小屋上方",6,true),
        new Item("cloud_rug","云朵地毯","铺上蓝蓝的云朵地毯",8,true),
        new Item("flower_planter","花开盆栽","让窗边绿植开出粉色小花",10,true),
        new Item("growth_fruit","成长果实","亲密度 +30，小伙伴立刻升 1 级",10,false),
        new Item("snacks","野餐点心礼包","点心 +2",3,false),
        new Item("party","伙伴庆祝派对","亲密度 +12，心情 +10",5,false)
    ));
    private RewardCatalog(){}
    public static List<Item> all(){return ITEMS;}
    public static Item get(String id){
        for(Item item:ITEMS)if(item.id.equals(id))return item;
        throw new IllegalArgumentException("未知礼物");
    }
    public static void checkAvailable(PetState pet,Item item){
        if(item.decoration&&pet.decorations.contains(item.id))throw new IllegalArgumentException("已经有这件装扮啦，可以免费布置或收起");
    }
    public static void redeemKnowledge(PetState pet,String id){
        Item item=get(id);
        if(!pet.hatched())throw new IllegalArgumentException("先孵化小伙伴，再一起挑礼物吧");
        checkAvailable(pet,item);
        if(pet.stars<item.cost)throw new IllegalArgumentException("金色知识星星还不够，答题就能获得哦");
        apply(pet,item);pet.stars-=item.cost;
    }
    public static void apply(PetState pet,Item item){
        if(item.decoration){pet.decorations.add(item.id);pet.equippedDecorations.add(item.id);}
        else switch(item.id){
            case "growth_fruit":pet.affection+=30;break;
            case "snacks":pet.snacks+=2;break;
            case "party":pet.affection+=12;pet.happiness+=10;break;
            default:throw new IllegalArgumentException("未知礼物");
        }
        pet.clamp();
    }
}
