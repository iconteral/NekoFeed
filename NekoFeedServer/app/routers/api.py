from fastapi import APIRouter, Depends, Query, HTTPException, BackgroundTasks
from fastapi.responses import Response
from sqlalchemy.orm import Session
from sqlalchemy import func
from typing import Optional
from app.database import get_db
from app.models import UpstreamFeed, FeedItem, UserLike, UserCollect, UserHistory, User
from app.schemas import FeedResponse
from app.auth import get_or_create_device_user
from app.services.feed_fetcher import fetch_and_process_feed
import logging

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api")

@router.get("/feed", response_model=FeedResponse)
def get_feed(
    category: Optional[str] = None,
    item_type: Optional[str] = None,
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    base_url: Optional[str] = None,
    current_user: Optional[User] = Depends(get_or_create_device_user),
    db: Session = Depends(get_db)
):
    query = db.query(FeedItem)
    if category == "video":
        query = query.filter(FeedItem.item_type == "video")
    elif category == "shopping":
        query = query.filter(FeedItem.item_type == "product")
    elif category and category != "featured":
        query = query.filter(FeedItem.category == category)
    if item_type:
         query = query.filter(FeedItem.item_type == item_type)
         
    total = query.count()
    items = query.order_by(FeedItem.published_at.desc()).offset(offset).limit(limit).all()
    
    # 批量查询当前用户的互动状态（避免 N+1）
    item_ids = [item.id for item in items]
    
    like_counts = {}
    collect_counts = {}
    click_counts = {}
    user_likes = set()
    user_collects = set()
    
    if item_ids:
        # 聚合查询 like_count / collect_count
        for item_id, cnt in db.query(UserLike.item_id, func.count()).filter(
            UserLike.item_id.in_(item_ids)
        ).group_by(UserLike.item_id).all():
            like_counts[item_id] = cnt
            
        for item_id, cnt in db.query(UserCollect.item_id, func.count()).filter(
            UserCollect.item_id.in_(item_ids)
        ).group_by(UserCollect.item_id).all():
            collect_counts[item_id] = cnt

        for item_id, cnt in db.query(UserHistory.item_id, func.count()).filter(
            UserHistory.item_id.in_(item_ids)
        ).group_by(UserHistory.item_id).all():
            click_counts[item_id] = cnt
        
        # 当前用户的互动
        if current_user:
            user_likes = {row.item_id for row in db.query(UserLike.item_id).filter(
                UserLike.user_id == current_user.id,
                UserLike.item_id.in_(item_ids)
            ).all()}
            user_collects = {row.item_id for row in db.query(UserCollect.item_id).filter(
                UserCollect.user_id == current_user.id,
                UserCollect.item_id.in_(item_ids)
            ).all()}
    
    result_items = []
    for item in items:
        item_dict = {
             "id": item.id,
             "title": item.title,
             "summary": item.summary,
             "content": item.content,
             "source_name": item.source_name,
             "source_url": item.source_url,
             "category": item.category,
             "item_type": item.item_type,
             "card_type": item.card_type,
             "tags": item.tags.split(',') if item.tags else [],
             "published_at": item.published_at,
             "brand": item.brand,
             "cta_text": item.cta_text,
             "price_text": item.price_text,
             "is_sponsored": item.is_sponsored or False,
             "ai_summary": item.ai_summary,
             "ai_tags": item.ai_tags.split(',') if item.ai_tags else [],
             "ai_reason": item.ai_reason,
             "ai_enriched": item.ai_enriched or False,
             # 互动状态
             "is_liked": item.id in user_likes,
             "is_collected": item.id in user_collects,
             "like_count": item.base_like_count + like_counts.get(item.id, 0),
             "collect_count": item.base_collect_count + collect_counts.get(item.id, 0),
             "click_count": item.base_click_count + click_counts.get(item.id, 0),
             "share_count": 0,
        }
        
        # Resolve Image URL
        image_url = item.image_url
        if item.local_image_path:
            image_url = f"{base_url.rstrip('/')}{item.local_image_path}" if base_url else item.local_image_path
        item_dict['image_url'] = image_url
        
        # Resolve Media URL
        media_url = item.media_url
        if item.local_media_path:
            media_url = f"{base_url.rstrip('/')}{item.local_media_path}" if base_url else item.local_media_path
        item_dict['media_url'] = media_url

        result_items.append(item_dict)

    return {"items": result_items, "limit": limit, "offset": offset, "total": total}

@router.get("/items/{id}")
def get_item(id: str, db: Session = Depends(get_db)):
    item = db.query(FeedItem).filter(FeedItem.id == id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")
    return item

@router.post("/refresh")
def refresh_all_feeds(background_tasks: BackgroundTasks, db: Session = Depends(get_db)):
    feeds = db.query(UpstreamFeed).filter(UpstreamFeed.enabled == True).all()
    for feed in feeds:
        background_tasks.add_task(fetch_and_process_feed, db, feed)
    return {"message": f"Started refresh for {len(feeds)} feeds"}

@router.post("/feeds/{id}/refresh")
def refresh_single_feed(id: int, background_tasks: BackgroundTasks, db: Session = Depends(get_db)):
    feed = db.query(UpstreamFeed).filter(UpstreamFeed.id == id).first()
    if not feed:
        raise HTTPException(status_code=404, detail="Feed not found")
    background_tasks.add_task(fetch_and_process_feed, db, feed)
    return {"message": f"Started refresh for feed: {feed.name}"}

@router.get("/rss.xml")
def generate_rss(db: Session = Depends(get_db), base_url: str = "http://localhost:8000"):
    items = db.query(FeedItem).order_by(FeedItem.published_at.desc()).limit(50).all()
    
    rss = '<?xml version="1.0" encoding="UTF-8" ?>\n<rss version="2.0">\n<channel>\n'
    rss += '  <title>Local Feed Aggregator</title>\n'
    rss += f'  <link>{base_url}</link>\n'
    rss += '  <description>Aggregated content for local use.</description>\n'
    
    for item in items:
        link = item.source_url or f"{base_url}/items/{item.id}"
        pubDate = item.published_at.strftime("%a, %d %b %Y %H:%M:%S GMT") if item.published_at else ""
        rss += '  <item>\n'
        rss += f'    <title><![CDATA[{item.title}]]></title>\n'
        rss += f'    <link>{link}</link>\n'
        if item.summary:
            rss += f'    <description><![CDATA[{item.summary}]]></description>\n'
        if pubDate:
            rss += f'    <pubDate>{pubDate}</pubDate>\n'
        rss += '  </item>\n'
        
    rss += '</channel>\n</rss>'
    return Response(content=rss, media_type="application/xml")
