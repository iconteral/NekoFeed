from pydantic import BaseModel, Field, HttpUrl
from typing import List, Optional
from datetime import datetime

# --- Upstream Feed Schemas ---

class UpstreamFeedBase(BaseModel):
    name: str
    url: str
    category: str = "tech"
    enabled: bool = True

class UpstreamFeedCreate(UpstreamFeedBase):
    pass

class UpstreamFeed(UpstreamFeedBase):
    id: int
    created_at: datetime
    last_fetch_at: Optional[datetime] = None
    last_status: Optional[str] = None
    last_error: Optional[str] = None

    class Config:
        from_attributes = True

# --- Feed Item Schemas ---

class FeedItemBase(BaseModel):
    title: str
    summary: Optional[str] = None
    content: Optional[str] = None
    source_name: Optional[str] = None
    source_url: Optional[str] = None
    category: str = "tech"
    item_type: str = "article"
    card_type: str = "large_image"
    image_url: Optional[str] = None
    media_url: Optional[str] = None
    tags: Optional[str] = None # Will be converted to List[str] in API response
    published_at: Optional[datetime] = None

class FeedItemCreate(FeedItemBase):
    pass

class FeedItemResponse(BaseModel):
    id: str
    title: str
    summary: Optional[str]
    content: Optional[str]
    sourceName: Optional[str] = Field(None, alias="source_name")
    sourceUrl: Optional[str] = Field(None, alias="source_url")
    category: str
    itemType: str = Field(..., alias="item_type")
    cardType: str = Field(..., alias="card_type")
    imageUrl: Optional[str] = Field(None, alias="image_url")
    mediaUrl: Optional[str] = Field(None, alias="media_url")
    tags: List[str] = []
    publishedAt: Optional[datetime] = Field(None, alias="published_at")
    brand: Optional[str] = None
    ctaText: Optional[str] = Field(None, alias="cta_text")
    priceText: Optional[str] = Field(None, alias="price_text")
    isSponsored: bool = Field(False, alias="is_sponsored")
    aiSummary: Optional[str] = Field(None, alias="ai_summary")
    aiTags: List[str] = Field([], alias="ai_tags")
    aiReason: Optional[str] = Field(None, alias="ai_reason")
    aiEnriched: bool = Field(False, alias="ai_enriched")
    
    # 互动状态字段
    isLiked: bool = Field(False, alias="is_liked")
    isCollected: bool = Field(False, alias="is_collected")
    likeCount: int = Field(0, alias="like_count")
    collectCount: int = Field(0, alias="collect_count")
    clickCount: int = Field(0, alias="click_count")
    shareCount: int = Field(0, alias="share_count")
    
    class Config:
        populate_by_name = True
        from_attributes = True

class FeedResponse(BaseModel):
    items: List[FeedItemResponse]
    limit: int
    offset: int
    total: int


# --- User Schemas ---

class UserCreate(BaseModel):
    username: str
    password: str

class UserLogin(BaseModel):
    username: str
    password: str

class UserResponse(BaseModel):
    id: int
    username: str
    avatar: Optional[str] = None
    bio: Optional[str] = None
    level: str = "Normal"
    is_active: bool = True
    created_at: datetime

    class Config:
        from_attributes = True

class UserUpdate(BaseModel):
    avatar: Optional[str] = None
    bio: Optional[str] = None

class ChangePassword(BaseModel):
    old_password: str
    new_password: str

class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"

class TokenData(BaseModel):
    user_id: Optional[int] = None


# --- User Interaction Schemas ---

class UserStats(BaseModel):
    likes_count: int = 0
    collections_count: int = 0
    history_count: int = 0

class ItemInteraction(BaseModel):
    is_liked: bool = False
    is_collected: bool = False
    like_count: int = 0
    collect_count: int = 0


# --- User Profile Analytics Schemas ---

class CategoryStat(BaseModel):
    category: str
    count: int
    percentage: float

class TagStat(BaseModel):
    tag: str
    count: int

class ContentTypeStat(BaseModel):
    item_type: str
    count: int
    percentage: float

class DailyActivity(BaseModel):
    date: str
    views: int
    likes: int
    collects: int

class ReadingPattern(BaseModel):
    hour: int
    count: int

class UserInterestProfile(BaseModel):
    top_categories: List[CategoryStat]
    top_tags: List[TagStat]
    content_type_preferences: List[ContentTypeStat]
    liked_categories: List[CategoryStat]
    collected_categories: List[CategoryStat]

class UserBehaviorStats(BaseModel):
    total_reading_time_seconds: int
    avg_reading_time_seconds: float
    total_items_viewed: int
    total_likes: int
    total_collects: int
    most_active_hour: Optional[int] = None
    daily_activities: List[DailyActivity]
    reading_patterns: List[ReadingPattern]

class UserEngagementMetrics(BaseModel):
    like_rate: float  # likes / views
    collect_rate: float  # collects / views
    avg_daily_views: float
    most_engaged_category: Optional[str] = None
    longest_streak_days: int

class UserProfile(BaseModel):
    user: UserResponse
    interests: UserInterestProfile
    behavior: UserBehaviorStats
    engagement: UserEngagementMetrics
    recent_likes: List[dict]
    recent_collects: List[dict]
    recent_history: List[dict]
