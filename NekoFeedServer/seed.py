import os
import hashlib
import time
from datetime import datetime
from app.database import engine, Base, SessionLocal
from app.models import UpstreamFeed, FeedItem

# Initialize the database
Base.metadata.create_all(bind=engine)
db = SessionLocal()

def seed_data():
    # 1. Add Default Feeds
    default_feeds = [
        {"name": "36Kr", "url": "https://36kr.com/feed", "category": "tech"},
        {"name": "SSPAI", "url": "https://sspai.com/feed", "category": "tech"},
        {"name": "IT Home", "url": "https://www.ithome.com/rss/", "category": "tech"},
        {"name": "iFanr", "url": "https://www.ifanr.com/feed", "category": "tech"},
        {"name": "BBC Tech", "url": "https://feeds.bbci.co.uk/news/technology/rss.xml", "category": "news"},
        {"name": "TechCrunch", "url": "https://techcrunch.com/feed/", "category": "tech"},
        {"name": "Hacker News", "url": "https://news.ycombinator.com/rss", "category": "tech"}
    ]

    for feed_data in default_feeds:
        existing = db.query(UpstreamFeed).filter(UpstreamFeed.url == feed_data["url"]).first()
        if not existing:
            feed = UpstreamFeed(**feed_data)
            db.add(feed)
            print(f"Added feed: {feed.name}")

    db.commit()

    # 2. Add Custom Ads
    custom_ads = [
         {
             "title": "【大图广告】阿里云双十一特惠",
             "summary": "阿里云年度狂欢，云服务器新老同享，低至XX元/年起。点击了解详情。",
             "category": "ad",
             "item_type": "ad",
             "card_type": "large_image",
             "image_url": "https://img.alicdn.com/tfs/TB1_zxCGVXXXXXuaXXXXXXXXXXX-720-300.jpg", 
             "tags": "cloud,sale",
             "source_name": "Alibaba Cloud",
             "brand": "Alibaba Cloud",
             "cta_text": "立即抢购",
             "price_text": "低至99元/年",
             "is_sponsored": True,
         },
         {
             "title": "【小图广告】星巴克双杯特惠",
             "summary": "下午茶时间，星巴克全场任意饮品双杯立减10元，快拉上小伙伴一起吧！",
             "category": "local",
             "item_type": "ad",
             "card_type": "small_image",
             "image_url": "https://images.unsplash.com/photo-1541167760496-1628856ab772?auto=format&fit=crop&w=300&q=80",
             "tags": "coffee,sale",
             "source_name": "Starbucks",
             "brand": "Starbucks",
             "cta_text": "领券专享",
             "price_text": "双杯立减10元",
             "is_sponsored": True,
         },
         {
             "title": "【视频广告】全新 iPhone 15 Pro",
             "summary": "钛金属外观，A17 Pro芯片，强大的影像系统。看视频了解更多。",
             "category": "ad",
             "item_type": "video",
             "card_type": "video",
             "image_url": "https://images.unsplash.com/photo-1695048064971-4a5fdfc972f7?auto=format&fit=crop&w=600&q=80",
             "media_url": "https://www.w3schools.com/html/mov_bbb.mp4", # Example safe video
             "tags": "apple,phone",
             "source_name": "Apple",
             "brand": "Apple",
             "cta_text": "了解更多",
             "price_text": "7999元起",
             "is_sponsored": True,
         },
         {
             "title": "【商品广告】索尼降噪耳机 WH-1000XM5",
             "summary": "行业领先的降噪技术，带来更纯粹的音乐体验。现在购买立享优惠。",
             "category": "ad",
             "item_type": "product",
             "card_type": "large_image",
             "image_url": "https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?auto=format&fit=crop&w=600&q=80",
             "tags": "audio,sony",
             "source_name": "Sony",
             "brand": "Sony",
             "cta_text": "立即购买",
             "price_text": "2499元",
             "is_sponsored": True,
         },
         {
             "title": "【本地生活广告】附近人气餐厅推荐：老北京烤鸭",
             "summary": "距离您 1.2km，评分 4.9 的老字号烤鸭店。提前预订享9折优惠。",
             "category": "local",
             "item_type": "ad",
             "card_type": "large_image",
             "image_url": "https://images.unsplash.com/photo-1574484284002-952d92456975?auto=format&fit=crop&w=600&q=80",
             "tags": "food,local",
             "source_name": "Dianping",
             "brand": "老北京烤鸭",
             "cta_text": "到店抢购",
             "price_text": "人均99元",
             "is_sponsored": True,
         }
     ]

    for ad in custom_ads:
        item_id = f"custom_{hashlib.md5(ad['title'].encode()).hexdigest()}"
        existing = db.query(FeedItem).filter(FeedItem.id == item_id).first()
        if not existing:
            ad['id'] = item_id
            ad['is_custom'] = True
            ad['published_at'] = datetime.utcnow()
            item = FeedItem(**ad)
            db.add(item)
            print(f"Added custom ad: {item.title}")

    db.commit()
    print("Database seeding completed.")
    db.close()

if __name__ == "__main__":
    seed_data()