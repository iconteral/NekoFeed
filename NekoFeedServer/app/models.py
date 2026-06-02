from sqlalchemy import Column, Integer, String, Boolean, DateTime, Text, ForeignKey
from datetime import datetime
from .database import Base

class UpstreamFeed(Base):
    __tablename__ = "upstream_feeds"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, index=True)
    url = Column(String, unique=True, index=True)
    category = Column(String, default="tech")
    enabled = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    last_fetch_at = Column(DateTime, nullable=True)
    last_status = Column(String, nullable=True) # "success" or "error"
    last_error = Column(Text, nullable=True)


class FeedItem(Base):
    __tablename__ = "feed_items"

    id = Column(String, primary_key=True, index=True) # Using a string hash/id
    upstream_feed_id = Column(Integer, ForeignKey("upstream_feeds.id"), nullable=True)
    title = Column(String)
    summary = Column(Text, nullable=True)
    content = Column(Text, nullable=True)
    source_name = Column(String, nullable=True)
    source_url = Column(String, nullable=True, index=True)
    category = Column(String, default="tech")
    item_type = Column(String, default="article") # article, ad, video, product
    card_type = Column(String, default="large_image") # large_image, small_image, video
    image_url = Column(String, nullable=True)
    media_url = Column(String, nullable=True)
    local_image_path = Column(String, nullable=True)
    local_media_path = Column(String, nullable=True)
    tags = Column(String, nullable=True) # Comma separated
    is_custom = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    published_at = Column(DateTime, nullable=True)
    raw_json = Column(Text, nullable=True)
