import os
from sqlalchemy.orm import Session
from app.models import SystemSetting

class LlmConfig:
    """Helper to fetch and store LLM configuration from SystemSetting DB table or env variables."""
    
    def __init__(self, db: Session = None):
        if db:
            self.base_url = self._get_db_setting(db, "llm_base_url") or os.getenv("LLM_BASE_URL", "")
            self.api_key = self._get_db_setting(db, "llm_api_key") or os.getenv("LLM_API_KEY", "")
            self.model = self._get_db_setting(db, "llm_model") or os.getenv("LLM_MODEL", "gpt-4o-mini")
        else:
            self.base_url = os.getenv("LLM_BASE_URL", "")
            self.api_key = os.getenv("LLM_API_KEY", "")
            self.model = os.getenv("LLM_MODEL", "gpt-4o-mini")
            
        self.max_tokens = int(os.getenv("LLM_MAX_TOKENS", "1024"))
        self.temperature = float(os.getenv("LLM_TEMPERATURE", "0.3"))
        self.timeout = int(os.getenv("LLM_TIMEOUT", "30"))

    def _get_db_setting(self, db: Session, key: str) -> str:
        setting = db.query(SystemSetting).filter(SystemSetting.key == key).first()
        return setting.value if setting else ""

    @property
    def is_configured(self) -> bool:
        return bool(self.base_url)

    @staticmethod
    def save_config(db: Session, base_url: str, api_key: str, model: str):
        for key, val in [("llm_base_url", base_url), ("llm_api_key", api_key), ("llm_model", model)]:
            setting = db.query(SystemSetting).filter(SystemSetting.key == key).first()
            if setting:
                setting.value = val.strip()
            else:
                setting = SystemSetting(key=key, value=val.strip())
                db.add(setting)
        db.commit()
