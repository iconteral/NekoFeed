from datetime import datetime, timedelta
from typing import Optional
from jose import JWTError, jwt
from passlib.context import CryptContext
from fastapi import Depends, HTTPException, status, Header
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from .database import get_db
from .models import User

SECRET_KEY = "neko-feed-secret-key-change-in-production"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_DAYS = 30

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
security = HTTPBearer()


def verify_password(plain_password: str, hashed_password: str) -> bool:
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    return pwd_context.hash(password)


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    expire = datetime.utcnow() + (expires_delta or timedelta(days=ACCESS_TOKEN_EXPIRE_DAYS))
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def decode_token(token: str) -> Optional[dict]:
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return payload
    except JWTError:
        return None


def _resolve_token(token: str, db: Session) -> Optional[User]:
    """从 token 解析用户"""
    payload = decode_token(token)
    if payload is None:
        return None
    user_id = payload.get("sub")
    if user_id is None:
        return None
    try:
        user_id = int(user_id)
    except (ValueError, TypeError):
        return None
    user = db.query(User).filter(User.id == user_id).first()
    if user and user.is_active:
        return user
    return None


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db)
) -> User:
    token = credentials.credentials
    user = _resolve_token(token, db)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token"
        )
    return user


def get_optional_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(HTTPBearer(auto_error=False)),
    db: Session = Depends(get_db)
) -> Optional[User]:
    if credentials is None:
        return None
    return _resolve_token(credentials.credentials, db)


def get_or_create_device_user(
    device_id: Optional[str] = Header(None, alias="X-Device-Id"),
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(HTTPBearer(auto_error=False)),
    db: Session = Depends(get_db)
) -> Optional[User]:
    """
    优先用 Bearer token 识别登录用户；
    否则用 X-Device-Id 找到或创建设备用户。
    """
    # 1. 尝试 Bearer token
    if credentials:
        user = _resolve_token(credentials.credentials, db)
        if user:
            return user

    # 2. 尝试 device_id
    if device_id:
        user = db.query(User).filter(User.device_id == device_id).first()
        if not user:
            user = User(
                username=f"device_{device_id[:8]}",
                device_id=device_id,
                is_device=True,
                hashed_password=None
            )
            db.add(user)
            db.commit()
            db.refresh(user)
        return user

    return None
