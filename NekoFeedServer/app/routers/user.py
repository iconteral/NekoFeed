from fastapi import APIRouter, Depends, HTTPException, status, Header
from sqlalchemy.orm import Session
from typing import Optional
from app.database import get_db
from app.models import User, UserLike, UserCollect, UserHistory
from app.schemas import (
    UserCreate, UserLogin, UserResponse, UserUpdate,
    ChangePassword, Token, UserStats
)
from app.auth import (
    get_password_hash, verify_password, create_access_token,
    get_current_user
)

router = APIRouter(prefix="/api/auth")


def _merge_device_interactions(db: Session, device_id: str, real_user_id: int):
    """将设备用户的 like/collect/history 迁移到真实用户"""
    device_user = db.query(User).filter(User.device_id == device_id).first()
    if not device_user or device_user.id == real_user_id:
        return
    
    # 迁移 likes（跳过已存在的）
    for like in db.query(UserLike).filter(UserLike.user_id == device_user.id).all():
        exists = db.query(UserLike).filter(
            UserLike.user_id == real_user_id,
            UserLike.item_id == like.item_id
        ).first()
        if not exists:
            like.user_id = real_user_id
        else:
            db.delete(like)
    
    # 迁移 collects
    for collect in db.query(UserCollect).filter(UserCollect.user_id == device_user.id).all():
        exists = db.query(UserCollect).filter(
            UserCollect.user_id == real_user_id,
            UserCollect.item_id == collect.item_id
        ).first()
        if not exists:
            collect.user_id = real_user_id
        else:
            db.delete(collect)
    
    # 迁移 history
    for history in db.query(UserHistory).filter(UserHistory.user_id == device_user.id).all():
        exists = db.query(UserHistory).filter(
            UserHistory.user_id == real_user_id,
            UserHistory.item_id == history.item_id
        ).first()
        if not exists:
            history.user_id = real_user_id
        else:
            db.delete(history)
    
    # 标记设备用户已绑定
    device_user.linked_user_id = real_user_id
    db.commit()


@router.post("/register", response_model=Token)
def register(
    user_data: UserCreate,
    device_id: Optional[str] = Header(None, alias="X-Device-Id"),
    db: Session = Depends(get_db)
):
    existing = db.query(User).filter(User.username == user_data.username).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Username already registered"
        )

    user = User(
        username=user_data.username,
        hashed_password=get_password_hash(user_data.password)
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    # 迁移设备用户的互动到新注册用户
    if device_id:
        _merge_device_interactions(db, device_id, user.id)

    token = create_access_token({"sub": str(user.id)})
    return {"access_token": token, "token_type": "bearer"}


@router.post("/login", response_model=Token)
def login(
    user_data: UserLogin,
    device_id: Optional[str] = Header(None, alias="X-Device-Id"),
    db: Session = Depends(get_db)
):
    user = db.query(User).filter(User.username == user_data.username).first()
    if not user or not verify_password(user_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password"
        )
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User is disabled"
        )

    # 迁移设备用户的互动到登录用户
    if device_id:
        _merge_device_interactions(db, device_id, user.id)

    token = create_access_token({"sub": str(user.id)})
    return {"access_token": token, "token_type": "bearer"}


@router.get("/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    return current_user


@router.put("/me", response_model=UserResponse)
def update_me(
    update_data: UserUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    if update_data.avatar is not None:
        current_user.avatar = update_data.avatar
    if update_data.bio is not None:
        current_user.bio = update_data.bio
    db.commit()
    db.refresh(current_user)
    return current_user


@router.post("/change-password")
def change_password(
    password_data: ChangePassword,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    if not verify_password(password_data.old_password, current_user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid old password"
        )
    current_user.hashed_password = get_password_hash(password_data.new_password)
    db.commit()
    return {"message": "Password changed successfully"}


@router.get("/stats", response_model=UserStats)
def get_user_stats(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    likes_count = db.query(UserLike).filter(UserLike.user_id == current_user.id).count()
    collects_count = db.query(UserCollect).filter(UserCollect.user_id == current_user.id).count()
    history_count = db.query(UserHistory).filter(UserHistory.user_id == current_user.id).count()

    return UserStats(
        likes_count=likes_count,
        collections_count=collects_count,
        history_count=history_count
    )
