from sqlalchemy import Column, Integer, String, Boolean, DateTime, Text, ForeignKey, UniqueConstraint
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

    # Commercial/ad fields
    brand = Column(String, nullable=True)
    cta_text = Column(String, nullable=True)
    price_text = Column(String, nullable=True)
    is_sponsored = Column(Boolean, default=False)

    # AI enriched fields
    ai_summary = Column(Text, nullable=True)
    ai_tags = Column(String, nullable=True) # Comma separated
    ai_reason = Column(String, nullable=True)
    ai_enriched = Column(Boolean, default=False)


class SystemSetting(Base):
    __tablename__ = "system_settings"

    key = Column(String, primary_key=True, index=True)
    value = Column(String, nullable=True)


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=True)  # nullable: 设备用户没有密码
    device_id = Column(String, unique=True, nullable=True, index=True)  # 设备唯一标识
    is_device = Column(Boolean, default=False)  # 标记设备用户
    linked_user_id = Column(Integer, nullable=True)  # 登录后绑定到真实用户
    avatar = Column(String, nullable=True)
    bio = Column(Text, nullable=True)
    level = Column(String, default="Normal") # Normal, Gold, VIP
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class UserLike(Base):
    __tablename__ = "user_likes"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    item_id = Column(String, ForeignKey("feed_items.id"), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    __table_args__ = (UniqueConstraint('user_id', 'item_id', name='uq_user_like'),)


class UserCollect(Base):
    __tablename__ = "user_collects"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    item_id = Column(String, ForeignKey("feed_items.id"), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    __table_args__ = (UniqueConstraint('user_id', 'item_id', name='uq_user_collect'),)


class UserHistory(Base):
    __tablename__ = "user_history"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    item_id = Column(String, ForeignKey("feed_items.id"), nullable=False)
    viewed_at = Column(DateTime, default=datetime.utcnow)
    duration = Column(Integer, default=0) # seconds
