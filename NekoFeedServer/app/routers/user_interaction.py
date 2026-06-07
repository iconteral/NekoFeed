from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.orm import Session
from sqlalchemy import func, extract
from typing import List, Optional
from datetime import datetime, timedelta
from collections import Counter
from app.database import get_db
from app.models import User, FeedItem, UserLike, UserCollect, UserHistory
from app.schemas import (
    ItemInteraction, FeedItemResponse, UserProfile, UserResponse,
    UserInterestProfile, UserBehaviorStats, UserEngagementMetrics,
    CategoryStat, TagStat, ContentTypeStat, DailyActivity, ReadingPattern
)
from app.auth import get_current_user, get_optional_user, get_or_create_device_user

router = APIRouter(prefix="/api")


@router.post("/items/{item_id}/like", response_model=ItemInteraction)
def toggle_like(
    item_id: str,
    current_user: User = Depends(get_or_create_device_user),
    db: Session = Depends(get_db)
):
    if not current_user:
        raise HTTPException(status_code=400, detail="需要登录或提供设备 ID")
    
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    existing = db.query(UserLike).filter(
        UserLike.user_id == current_user.id,
        UserLike.item_id == item_id
    ).first()

    if existing:
        db.delete(existing)
        is_liked = False
    else:
        like = UserLike(user_id=current_user.id, item_id=item_id)
        db.add(like)
        is_liked = True

    db.commit()

    like_count = item.base_like_count + db.query(UserLike).filter(
        UserLike.item_id == item_id
    ).count()
    collect_count = item.base_collect_count + db.query(UserCollect).filter(
        UserCollect.item_id == item_id
    ).count()
    is_collected = db.query(UserCollect).filter(
        UserCollect.user_id == current_user.id,
        UserCollect.item_id == item_id
    ).first() is not None

    return ItemInteraction(
        is_liked=is_liked,
        is_collected=is_collected,
        like_count=like_count,
        collect_count=collect_count
    )


@router.post("/items/{item_id}/collect", response_model=ItemInteraction)
def toggle_collect(
    item_id: str,
    current_user: User = Depends(get_or_create_device_user),
    db: Session = Depends(get_db)
):
    if not current_user:
        raise HTTPException(status_code=400, detail="需要登录或提供设备 ID")
    
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    existing = db.query(UserCollect).filter(
        UserCollect.user_id == current_user.id,
        UserCollect.item_id == item_id
    ).first()

    if existing:
        db.delete(existing)
        is_collected = False
    else:
        collect = UserCollect(user_id=current_user.id, item_id=item_id)
        db.add(collect)
        is_collected = True

    db.commit()

    like_count = item.base_like_count + db.query(UserLike).filter(
        UserLike.item_id == item_id
    ).count()
    collect_count = item.base_collect_count + db.query(UserCollect).filter(
        UserCollect.item_id == item_id
    ).count()
    is_liked = db.query(UserLike).filter(
        UserLike.user_id == current_user.id,
        UserLike.item_id == item_id
    ).first() is not None

    return ItemInteraction(
        is_liked=is_liked,
        is_collected=is_collected,
        like_count=like_count,
        collect_count=collect_count
    )


@router.post("/items/{item_id}/history")
def record_history(
    item_id: str,
    duration: int = 0,
    current_user: User = Depends(get_or_create_device_user),
    db: Session = Depends(get_db)
):
    if not current_user:
        raise HTTPException(status_code=400, detail="需要登录或提供设备 ID")
    
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    existing = db.query(UserHistory).filter(
        UserHistory.user_id == current_user.id,
        UserHistory.item_id == item_id
    ).first()

    if existing:
        existing.viewed_at = datetime.utcnow()
        existing.duration = duration
    else:
        history = UserHistory(
            user_id=current_user.id,
            item_id=item_id,
            duration=duration
        )
        db.add(history)

    db.commit()
    return {"message": "History recorded"}


@router.get("/items/{item_id}/interaction", response_model=ItemInteraction)
def get_item_interaction(
    item_id: str,
    current_user: Optional[User] = Depends(get_optional_user),
    db: Session = Depends(get_db)
):
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    like_count = item.base_like_count + db.query(UserLike).filter(
        UserLike.item_id == item_id
    ).count()
    collect_count = item.base_collect_count + db.query(UserCollect).filter(
        UserCollect.item_id == item_id
    ).count()

    is_liked = False
    is_collected = False

    if current_user:
        is_liked = db.query(UserLike).filter(
            UserLike.user_id == current_user.id,
            UserLike.item_id == item_id
        ).first() is not None
        is_collected = db.query(UserCollect).filter(
            UserCollect.user_id == current_user.id,
            UserCollect.item_id == item_id
        ).first() is not None

    return ItemInteraction(
        is_liked=is_liked,
        is_collected=is_collected,
        like_count=like_count,
        collect_count=collect_count
    )


@router.get("/user/likes")
def get_user_likes(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    query = db.query(FeedItem).join(
        UserLike, UserLike.item_id == FeedItem.id
    ).filter(UserLike.user_id == current_user.id)

    total = query.count()
    items = query.order_by(UserLike.created_at.desc()).offset(offset).limit(limit).all()

    return {
        "items": _items_to_dict(db, items, current_user.id),
        "total": total,
        "limit": limit,
        "offset": offset
    }


@router.get("/user/collections")
def get_user_collections(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    query = db.query(FeedItem).join(
        UserCollect, UserCollect.item_id == FeedItem.id
    ).filter(UserCollect.user_id == current_user.id)

    total = query.count()
    items = query.order_by(UserCollect.created_at.desc()).offset(offset).limit(limit).all()

    return {
        "items": _items_to_dict(db, items, current_user.id),
        "total": total,
        "limit": limit,
        "offset": offset
    }


@router.get("/user/history")
def get_user_history(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    query = db.query(FeedItem).join(
        UserHistory, UserHistory.item_id == FeedItem.id
    ).filter(UserHistory.user_id == current_user.id)

    total = query.count()
    items = query.order_by(UserHistory.viewed_at.desc()).offset(offset).limit(limit).all()

    return {
        "items": _items_to_dict(db, items, current_user.id),
        "total": total,
        "limit": limit,
        "offset": offset
    }


@router.delete("/user/history")
def clear_user_history(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    db.query(UserHistory).filter(UserHistory.user_id == current_user.id).delete()
    db.commit()
    return {"message": "History cleared"}


@router.get("/user/profile", response_model=UserProfile)
def get_user_profile(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    # 1. 基础互动数据
    likes = db.query(UserLike).filter(UserLike.user_id == current_user.id).all()
    collects = db.query(UserCollect).filter(UserCollect.user_id == current_user.id).all()
    history = db.query(UserHistory).filter(UserHistory.user_id == current_user.id).all()

    like_item_ids = [l.item_id for l in likes]
    collect_item_ids = [c.item_id for c in collects]
    history_item_ids = [h.item_id for h in history]

    # 2. 获取关联的 FeedItem
    liked_items = {i.id: i for i in db.query(FeedItem).filter(FeedItem.id.in_(like_item_ids)).all()} if like_item_ids else {}
    collected_items = {i.id: i for i in db.query(FeedItem).filter(FeedItem.id.in_(collect_item_ids)).all()} if collect_item_ids else {}
    history_items = {i.id: i for i in db.query(FeedItem).filter(FeedItem.id.in_(history_item_ids)).all()} if history_item_ids else {}

    # 3. 兴趣画像 - 分类统计
    def _calc_category_stats(items_dict, count):
        counter = Counter()
        for item in items_dict.values():
            if item.category:
                counter[item.category] += 1
        total = sum(counter.values()) or 1
        return [
            CategoryStat(category=cat, count=c, percentage=round(c / total * 100, 1))
            for cat, c in counter.most_common(10)
        ]

    all_viewed_categories = Counter()
    for h in history:
        item = history_items.get(h.item_id)
        if item and item.category:
            all_viewed_categories[item.category] += 1

    total_views = sum(all_viewed_categories.values()) or 1
    top_categories = [
        CategoryStat(category=cat, count=c, percentage=round(c / total_views * 100, 1))
        for cat, c in all_viewed_categories.most_common(10)
    ]

    # 4. 标签统计
    tag_counter = Counter()
    for h in history:
        item = history_items.get(h.item_id)
        if item and item.tags:
            for tag in item.tags.split(','):
                tag = tag.strip()
                if tag:
                    tag_counter[tag] += 1
    top_tags = [TagStat(tag=t, count=c) for t, c in tag_counter.most_common(20)]

    # 5. 内容类型偏好
    type_counter = Counter()
    for h in history:
        item = history_items.get(h.item_id)
        if item and item.item_type:
            type_counter[item.item_type] += 1
    total_type = sum(type_counter.values()) or 1
    content_type_preferences = [
        ContentTypeStat(item_type=t, count=c, percentage=round(c / total_type * 100, 1))
        for t, c in type_counter.most_common()
    ]

    # 6. 点赞/收藏的分类统计
    liked_categories = _calc_category_stats(liked_items, len(likes))
    collected_categories = _calc_category_stats(collected_items, len(collects))

    interests = UserInterestProfile(
        top_categories=top_categories,
        top_tags=top_tags,
        content_type_preferences=content_type_preferences,
        liked_categories=liked_categories,
        collected_categories=collected_categories
    )

    # 7. 行为统计
    total_reading_time = sum(h.duration or 0 for h in history)
    avg_reading_time = total_reading_time / len(history) if history else 0

    # 每日活动统计（最近30天）
    thirty_days_ago = datetime.utcnow() - timedelta(days=30)
    recent_history = [h for h in history if h.viewed_at and h.viewed_at >= thirty_days_ago]
    recent_likes = [l for l in likes if l.created_at and l.created_at >= thirty_days_ago]
    recent_collects = [c for c in collects if c.created_at and c.created_at >= thirty_days_ago]

    daily_views = Counter()
    daily_likes = Counter()
    daily_collects = Counter()

    for h in recent_history:
        daily_views[h.viewed_at.strftime("%Y-%m-%d")] += 1
    for l in recent_likes:
        daily_likes[l.created_at.strftime("%Y-%m-%d")] += 1
    for c in recent_collects:
        daily_collects[c.created_at.strftime("%Y-%m-%d")] += 1

    all_dates = set(list(daily_views.keys()) + list(daily_likes.keys()) + list(daily_collects.keys()))
    daily_activities = [
        DailyActivity(date=d, views=daily_views.get(d, 0), likes=daily_likes.get(d, 0), collects=daily_collects.get(d, 0))
        for d in sorted(all_dates)
    ]

    # 阅读时段分析
    hour_counter = Counter()
    for h in history:
        if h.viewed_at:
            hour_counter[h.viewed_at.hour] += 1
    reading_patterns = [ReadingPattern(hour=h, count=hour_counter.get(h, 0)) for h in range(24)]
    most_active_hour = hour_counter.most_common(1)[0][0] if hour_counter else None

    behavior = UserBehaviorStats(
        total_reading_time_seconds=total_reading_time,
        avg_reading_time_seconds=round(avg_reading_time, 1),
        total_items_viewed=len(history),
        total_likes=len(likes),
        total_collects=len(collects),
        most_active_hour=most_active_hour,
        daily_activities=daily_activities,
        reading_patterns=reading_patterns
    )

    # 8. 参与度指标
    like_rate = len(likes) / len(history) if history else 0
    collect_rate = len(collects) / len(history) if history else 0

    # 最活跃分类（点赞+收藏最多的）
    engagement_counter = Counter()
    for item in liked_items.values():
        if item.category:
            engagement_counter[item.category] += 1
    for item in collected_items.values():
        if item.category:
            engagement_counter[item.category] += 1
    most_engaged_category = engagement_counter.most_common(1)[0][0] if engagement_counter else None

    # 连续活跃天数
    if history:
        view_dates = sorted(set(h.viewed_at.date() for h in history if h.viewed_at))
        max_streak = 1
        current_streak = 1
        for i in range(1, len(view_dates)):
            if (view_dates[i] - view_dates[i - 1]).days == 1:
                current_streak += 1
                max_streak = max(max_streak, current_streak)
            else:
                current_streak = 1
    else:
        max_streak = 0

    avg_daily_views = len(history) / 30 if history else 0

    engagement = UserEngagementMetrics(
        like_rate=round(like_rate, 3),
        collect_rate=round(collect_rate, 3),
        avg_daily_views=round(avg_daily_views, 1),
        most_engaged_category=most_engaged_category,
        longest_streak_days=max_streak
    )

    # 9. 最近的互动列表（取最近10条）
    recent_likes_list = [
        _item_to_dict(liked_items[l.item_id])
        for l in sorted(likes, key=lambda x: x.created_at, reverse=True)[:10]
        if l.item_id in liked_items
    ]
    recent_collects_list = [
        _item_to_dict(collected_items[c.item_id])
        for c in sorted(collects, key=lambda x: x.created_at, reverse=True)[:10]
        if c.item_id in collected_items
    ]
    recent_history_list = [
        {**_item_to_dict(history_items[h.item_id]), "viewed_at": h.viewed_at.isoformat() if h.viewed_at else None, "duration": h.duration}
        for h in sorted(history, key=lambda x: x.viewed_at, reverse=True)[:10]
        if h.item_id in history_items
    ]

    return UserProfile(
        user=UserResponse.model_validate(current_user),
        interests=interests,
        behavior=behavior,
        engagement=engagement,
        recent_likes=recent_likes_list,
        recent_collects=recent_collects_list,
        recent_history=recent_history_list
    )


def _items_to_dict(db: Session, items: List[FeedItem], user_id: int) -> List[dict]:
    item_ids = [item.id for item in items]
    if not item_ids:
        return []

    like_counts = dict(
        db.query(UserLike.item_id, func.count(UserLike.id))
        .filter(UserLike.item_id.in_(item_ids))
        .group_by(UserLike.item_id)
        .all()
    )
    collect_counts = dict(
        db.query(UserCollect.item_id, func.count(UserCollect.id))
        .filter(UserCollect.item_id.in_(item_ids))
        .group_by(UserCollect.item_id)
        .all()
    )
    user_likes = {
        item_id
        for item_id, in db.query(UserLike.item_id).filter(
            UserLike.user_id == user_id,
            UserLike.item_id.in_(item_ids)
        ).all()
    }
    user_collects = {
        item_id
        for item_id, in db.query(UserCollect.item_id).filter(
            UserCollect.user_id == user_id,
            UserCollect.item_id.in_(item_ids)
        ).all()
    }

    return [
        _item_to_dict(
            item,
            is_liked=item.id in user_likes,
            is_collected=item.id in user_collects,
            like_count=item.base_like_count + like_counts.get(item.id, 0),
            collect_count=item.base_collect_count + collect_counts.get(item.id, 0)
        )
        for item in items
    ]


def _item_to_dict(
    item: FeedItem,
    is_liked: bool = False,
    is_collected: bool = False,
    like_count: int = 0,
    collect_count: int = 0
) -> dict:
    return {
        "id": item.id,
        "title": item.title,
        "summary": item.summary,
        "content": item.content,
        "source_name": item.source_name,
        "source_url": item.source_url,
        "category": item.category,
        "item_type": item.item_type,
        "card_type": item.card_type,
        "image_url": item.image_url,
        "media_url": item.media_url,
        "tags": item.tags.split(',') if item.tags else [],
        "published_at": item.published_at.isoformat() if item.published_at else None,
        "brand": item.brand,
        "cta_text": item.cta_text,
        "price_text": item.price_text,
        "is_sponsored": item.is_sponsored or False,
        "ai_summary": item.ai_summary,
        "ai_tags": item.ai_tags.split(',') if item.ai_tags else [],
        "ai_reason": item.ai_reason,
        "ai_enriched": item.ai_enriched or False,
        "is_liked": is_liked,
        "is_collected": is_collected,
        "like_count": like_count,
        "collect_count": collect_count,
    }
