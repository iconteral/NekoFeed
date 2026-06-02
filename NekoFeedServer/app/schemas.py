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
