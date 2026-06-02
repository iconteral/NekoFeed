from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime
from app.database import get_db
from app.models import User, FeedItem, UserLike, UserCollect, UserHistory
from app.schemas import ItemInteraction, FeedItemResponse
from app.auth import get_current_user, get_optional_user

router = APIRouter(prefix="/api")


@router.post("/items/{item_id}/like", response_model=ItemInteraction)
def toggle_like(
    item_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
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

    like_count = db.query(UserLike).filter(UserLike.item_id == item_id).count()
    collect_count = db.query(UserCollect).filter(UserCollect.item_id == item_id).count()
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
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
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

    like_count = db.query(UserLike).filter(UserLike.item_id == item_id).count()
    collect_count = db.query(UserCollect).filter(UserCollect.item_id == item_id).count()
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
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
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
    like_count = db.query(UserLike).filter(UserLike.item_id == item_id).count()
    collect_count = db.query(UserCollect).filter(UserCollect.item_id == item_id).count()

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
        "items": [_item_to_dict(item) for item in items],
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
        "items": [_item_to_dict(item) for item in items],
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
        "items": [_item_to_dict(item) for item in items],
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


def _item_to_dict(item: FeedItem) -> dict:
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
        "published_at": item.published_at
    }
