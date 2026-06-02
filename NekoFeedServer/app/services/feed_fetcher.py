import feedparser
import logging
import httpx
from datetime import datetime
from sqlalchemy.orm import Session
from app.models import UpstreamFeed, FeedItem
from app.services.item_normalizer import normalize_feed_item
from app.services.media_cache import download_media

logger = logging.getLogger(__name__)

async def process_feed_item(db: Session, normalized_data: dict):
    # Check if item already exists by ID
    existing_item = db.query(FeedItem).filter(FeedItem.id == normalized_data['id']).first()
    if existing_item:
        return # Skip

    # Attempt to download media if present
    image_url = normalized_data.get('image_url')
    if image_url:
        local_path = await download_media(image_url, is_video=False)
        if local_path:
            normalized_data['local_image_path'] = local_path
        else:
             # According to requirements: "失败时删除该条item" (If media download fails, skip item)
             logger.warning(f"Skipping item {normalized_data['id']} due to failed media download: {image_url}")
             return

    new_item = FeedItem(**normalized_data)
    db.add(new_item)
    db.commit()

async def fetch_and_process_feed(db: Session, feed: UpstreamFeed):
    try:
        logger.info(f"Fetching feed: {feed.url}")
        
        # Use httpx to fetch async
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(feed.url, follow_redirects=True)
            response.raise_for_status()
            raw_data = response.text

        # Parse with feedparser
        parsed_feed = feedparser.parse(raw_data)
        
        # Limit to 20 entries to avoid DB flooding during sync
        entries = parsed_feed.entries[:20]
        
        for entry in entries:
            normalized_data = normalize_feed_item(
                entry=entry,
                feed_id=feed.id,
                feed_name=feed.name,
                feed_category=feed.category
            )
            await process_feed_item(db, normalized_data)

        # Update feed status
        feed.last_fetch_at = datetime.utcnow()
        feed.last_status = "success"
        feed.last_error = None
        db.commit()

    except Exception as e:
        logger.error(f"Error fetching feed {feed.url}: {e}")
        feed.last_status = "error"
        feed.last_error = str(e)
        db.commit()
