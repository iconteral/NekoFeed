'use client';

import { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import { Heart, Bookmark, Share2, Search, Sparkles, Play, ChevronDown, BarChart3, Home, User, Volume2, VolumeX } from 'lucide-react';
import { useAdContext, AdChannel, AdItem } from './context/AdContext';

/* ─── Skeleton Card ─── */
function SkeletonCard({ type }: { type: 'large' | 'small' }) {
  if (type === 'large') {
    return (
      <div className="skeleton-card">
        <div className="skeleton" style={{ width: '100%', height: '220px', borderRadius: 0 }} />
        <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div className="skeleton" style={{ width: '40%', height: '12px' }} />
          <div className="skeleton" style={{ width: '85%', height: '18px' }} />
          <div className="skeleton" style={{ width: '100%', height: '48px' }} />
          <div style={{ display: 'flex', gap: '8px' }}>
            <div className="skeleton" style={{ width: '56px', height: '24px', borderRadius: '12px' }} />
            <div className="skeleton" style={{ width: '56px', height: '24px', borderRadius: '12px' }} />
            <div className="skeleton" style={{ width: '56px', height: '24px', borderRadius: '12px' }} />
          </div>
        </div>
      </div>
    );
  }
  return (
    <div className="skeleton-card">
      <div style={{ display: 'flex', padding: '16px', gap: '16px' }}>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div className="skeleton" style={{ width: '90%', height: '16px' }} />
          <div className="skeleton" style={{ width: '70%', height: '12px' }} />
          <div className="skeleton" style={{ width: '50%', height: '12px' }} />
        </div>
        <div className="skeleton" style={{ width: '88px', height: '88px', flexShrink: 0 }} />
      </div>
    </div>
  );
}

/* ─── Toast Component ─── */
function Toast({ message, onDone }: { message: string; onDone: () => void }) {
  const [exiting, setExiting] = useState(false);
  useEffect(() => {
    const t1 = setTimeout(() => setExiting(true), 1800);
    const t2 = setTimeout(onDone, 2100);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [onDone]);
  return <div className={`toast ${exiting ? 'exit' : ''}`}>{message}</div>;
}

/* ─── Ad Card Component ─── */
function AdCard({ ad, onCardClick, onLike, onCollect, onShare, onTagClick }: {
  ad: AdItem;
  onCardClick: () => void;
  onLike: () => void;
  onCollect: () => void;
  onShare: () => void;
  onTagClick: (tag: string) => void;
}) {
  const [videoMuted, setVideoMuted] = useState(true);
  const videoRef = useRef<HTMLVideoElement>(null);

  const handleMuteToggle = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (videoRef.current) {
      videoRef.current.muted = !videoRef.current.muted;
      setVideoMuted(!videoMuted);
    }
  };

  const actionBtn = (e: React.MouseEvent, fn: () => void) => { e.stopPropagation(); fn(); };

  if (ad.type === 'LARGE_IMAGE') {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
        className="glass-card"
        style={{ cursor: 'pointer' }}
        onClick={onCardClick}
      >
        <div style={{ width: '100%', height: '220px', overflow: 'hidden', position: 'relative' }}>
          <img src={ad.imageUrl} alt={ad.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
          <div style={{ position: 'absolute', top: '12px', left: '12px', background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(8px)', borderRadius: 'var(--radius-full)', padding: '4px 10px', fontSize: '0.6875rem', color: '#fff', fontWeight: 600, letterSpacing: '0.02em' }}>
            {ad.brand} · 推荐
          </div>
        </div>
        <div style={{ padding: '14px 16px 16px' }}>
          <h2 style={{ fontSize: '1.0625rem', fontWeight: 700, marginBottom: '10px', lineHeight: 1.4, letterSpacing: '-0.2px' }}>{ad.title}</h2>
          <div style={{ backgroundColor: 'var(--color-primary-soft)', padding: '10px 12px', borderRadius: 'var(--radius-sm)', marginBottom: '10px', display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
            <Sparkles size={15} color="var(--color-primary)" style={{ flexShrink: 0, marginTop: '2px' }} />
            <p style={{ fontSize: '0.8125rem', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>{ad.aiSummary}</p>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '14px' }}>
            {ad.aiTags.map(tag => (
              <span key={tag} className="tag-chip default" onClick={(e) => { e.stopPropagation(); onTagClick(tag); }}>{tag}</span>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '12px', borderTop: '1px solid rgba(128,128,128,0.1)' }}>
            <div style={{ display: 'flex', gap: '18px' }}>
              <button onClick={(e) => actionBtn(e, onLike)} style={{ display: 'flex', alignItems: 'center', gap: '5px', background: 'none', border: 'none', color: ad.isLiked ? 'var(--color-accent-red)' : 'var(--color-text-muted)', cursor: 'pointer', fontFamily: 'inherit' }}>
                <Heart size={18} fill={ad.isLiked ? 'currentColor' : 'none'} />
                <span style={{ fontSize: '0.8125rem' }}>{ad.likeCount}</span>
              </button>
              <button onClick={(e) => actionBtn(e, onCollect)} style={{ display: 'flex', alignItems: 'center', gap: '5px', background: 'none', border: 'none', color: ad.isCollected ? 'var(--color-accent-orange)' : 'var(--color-text-muted)', cursor: 'pointer', fontFamily: 'inherit' }}>
                <Bookmark size={18} fill={ad.isCollected ? 'currentColor' : 'none'} />
                <span style={{ fontSize: '0.8125rem' }}>{ad.collectCount}</span>
              </button>
            </div>
            <button onClick={(e) => actionBtn(e, onShare)} style={{ background: 'none', border: 'none', color: 'var(--color-text-muted)', cursor: 'pointer' }}>
              <Share2 size={18} />
            </button>
          </div>
        </div>
      </motion.div>
    );
  }

  if (ad.type === 'VIDEO') {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
        className="glass-card"
        style={{ cursor: 'pointer' }}
        onClick={onCardClick}
      >
        <div style={{ width: '100%', height: '260px', overflow: 'hidden', position: 'relative', background: '#000' }}>
          <video
            ref={videoRef}
            src={ad.videoUrl}
            poster={ad.imageUrl}
            autoPlay
            muted={videoMuted}
            loop
            playsInline
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
          <div style={{ position: 'absolute', top: '12px', left: '12px', background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(8px)', borderRadius: 'var(--radius-full)', padding: '4px 10px', fontSize: '0.6875rem', color: '#fff', fontWeight: 600 }}>
            {ad.brand} · 视频
          </div>
          <button
            onClick={handleMuteToggle}
            style={{ position: 'absolute', bottom: '12px', right: '12px', background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(8px)', border: 'none', borderRadius: 'var(--radius-full)', width: '36px', height: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', cursor: 'pointer' }}
          >
            {videoMuted ? <VolumeX size={16} /> : <Volume2 size={16} />}
          </button>
          <div style={{ position: 'absolute', bottom: '12px', left: '12px', background: 'rgba(92,77,255,0.85)', backdropFilter: 'blur(8px)', borderRadius: 'var(--radius-full)', padding: '4px 10px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Play size={12} fill="#fff" color="#fff" />
            <span style={{ fontSize: '0.6875rem', color: '#fff', fontWeight: 600 }}>播放中</span>
          </div>
        </div>
        <div style={{ padding: '14px 16px 16px' }}>
          <h2 style={{ fontSize: '1.0625rem', fontWeight: 700, marginBottom: '10px', lineHeight: 1.4 }}>{ad.title}</h2>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '14px' }}>
            {ad.aiTags.map(tag => (
              <span key={tag} className="tag-chip default" onClick={(e) => { e.stopPropagation(); onTagClick(tag); }}>{tag}</span>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '12px', borderTop: '1px solid rgba(128,128,128,0.1)' }}>
            <div style={{ display: 'flex', gap: '18px' }}>
              <button onClick={(e) => actionBtn(e, onLike)} style={{ display: 'flex', alignItems: 'center', gap: '5px', background: 'none', border: 'none', color: ad.isLiked ? 'var(--color-accent-red)' : 'var(--color-text-muted)', cursor: 'pointer', fontFamily: 'inherit' }}>
                <Heart size={18} fill={ad.isLiked ? 'currentColor' : 'none'} />
                <span style={{ fontSize: '0.8125rem' }}>{ad.likeCount}</span>
              </button>
              <button onClick={(e) => actionBtn(e, onCollect)} style={{ display: 'flex', alignItems: 'center', gap: '5px', background: 'none', border: 'none', color: ad.isCollected ? 'var(--color-accent-orange)' : 'var(--color-text-muted)', cursor: 'pointer', fontFamily: 'inherit' }}>
                <Bookmark size={18} fill={ad.isCollected ? 'currentColor' : 'none'} />
                <span style={{ fontSize: '0.8125rem' }}>{ad.collectCount}</span>
              </button>
            </div>
            <button onClick={(e) => actionBtn(e, onShare)} style={{ background: 'none', border: 'none', color: 'var(--color-text-muted)', cursor: 'pointer' }}>
              <Share2 size={18} />
            </button>
          </div>
        </div>
      </motion.div>
    );
  }

  // SMALL_IMAGE
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35 }}
      className="glass-card"
      style={{ cursor: 'pointer' }}
      onClick={onCardClick}
    >
      <div style={{ display: 'flex', padding: '14px 16px', gap: '14px' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <h2 style={{ fontSize: '0.9375rem', fontWeight: 700, marginBottom: '6px', lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{ad.title}</h2>
          <div style={{ display: 'flex', gap: '4px', alignItems: 'flex-start', marginBottom: '8px' }}>
            <Sparkles size={13} color="var(--color-primary)" style={{ flexShrink: 0, marginTop: '2px' }} />
            <p style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{ad.aiSummary}</p>
          </div>
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '8px' }}>
            {ad.aiTags.slice(0, 2).map(tag => (
              <span key={tag} className="tag-chip default" style={{ fontSize: '0.6875rem', padding: '3px 8px' }} onClick={(e) => { e.stopPropagation(); onTagClick(tag); }}>{tag}</span>
            ))}
          </div>
          <div style={{ display: 'flex', gap: '14px', color: 'var(--color-text-muted)' }}>
            <button onClick={(e) => actionBtn(e, onLike)} style={{ display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: 'none', color: ad.isLiked ? 'var(--color-accent-red)' : 'inherit', cursor: 'pointer', fontFamily: 'inherit', fontSize: '0.75rem' }}>
              <Heart size={14} fill={ad.isLiked ? 'currentColor' : 'none'} />
              {ad.likeCount}
            </button>
            <button onClick={(e) => actionBtn(e, onCollect)} style={{ display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: 'none', color: ad.isCollected ? 'var(--color-accent-orange)' : 'inherit', cursor: 'pointer', fontFamily: 'inherit', fontSize: '0.75rem' }}>
              <Bookmark size={14} fill={ad.isCollected ? 'currentColor' : 'none'} />
              {ad.collectCount}
            </button>
          </div>
        </div>
        <div style={{ width: '88px', height: '88px', flexShrink: 0, borderRadius: 'var(--radius-sm)', overflow: 'hidden' }}>
          <img src={ad.imageUrl} alt={ad.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
        </div>
      </div>
    </motion.div>
  );
}

/* ─── Main Feed Page ─── */
export default function FeedPage() {
  const router = useRouter();
  const { ads, toggleLike, toggleCollect, incrementClick, incrementShare, incrementExposure, getAdsByChannel } = useAdContext();
  const [activeTab, setActiveTab] = useState<AdChannel>('精选');
  const [filterTag, setFilterTag] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [exposedIds] = useState<Set<string>>(() => new Set());
  const feedRef = useRef<HTMLDivElement>(null);
  const observerRef = useRef<IntersectionObserver | null>(null);

  const tabs: AdChannel[] = ['精选', '电商', '本地', '视频'];

  // Simulate initial load
  useEffect(() => {
    const timer = setTimeout(() => setIsLoading(false), 1200);
    return () => clearTimeout(timer);
  }, []);

  // Get filtered ads
  let filteredAds = getAdsByChannel(activeTab);
  if (filterTag) {
    filteredAds = filteredAds.filter(ad => ad.aiTags.includes(filterTag));
  }

  // Exposure tracking with IntersectionObserver
  useEffect(() => {
    if (isLoading) return;
    observerRef.current = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const id = entry.target.getAttribute('data-ad-id');
          if (id && !exposedIds.has(id)) {
            exposedIds.add(id);
            incrementExposure(id);
          }
        }
      });
    }, { threshold: 0.5 });

    const cards = document.querySelectorAll('[data-ad-id]');
    cards.forEach(card => observerRef.current?.observe(card));
    return () => observerRef.current?.disconnect();
  }, [isLoading, filteredAds, incrementExposure, exposedIds]);

  const handleCardClick = (id: string) => {
    incrementClick(id);
    router.push(`/ad/${id}`);
  };

  const handleRefresh = () => {
    setIsRefreshing(true);
    setTimeout(() => setIsRefreshing(false), 1500);
  };

  const handleLoadMore = () => {
    if (isLoadingMore) return;
    setIsLoadingMore(true);
    setTimeout(() => setIsLoadingMore(false), 1200);
  };

  const handleTabChange = (tab: AdChannel) => {
    setActiveTab(tab);
    setFilterTag(null);
  };

  const handleTagClick = (tag: string) => {
    setFilterTag(prev => prev === tag ? null : tag);
  };

  const handleShare = (id: string) => {
    incrementShare(id);
    setToastMsg('链接已复制，快去分享吧 🎉');
  };

  // Scroll-to-bottom detection for load more
  useEffect(() => {
    const handleScroll = () => {
      const el = feedRef.current;
      if (!el) return;
      const scrollBottom = window.innerHeight + window.scrollY;
      const docHeight = document.documentElement.scrollHeight;
      if (docHeight - scrollBottom < 200) {
        handleLoadMore();
      }
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [isLoadingMore]);

  return (
    <div ref={feedRef} style={{ paddingBottom: '80px' }}>
      {/* 顶部导航区 */}
      <header className="glass-panel" style={{
        position: 'sticky',
        top: 0,
        zIndex: 20,
        padding: '14px 20px 0',
        display: 'flex',
        flexDirection: 'column',
        gap: '0'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ width: '28px', height: '28px', borderRadius: 'var(--radius-sm)', background: 'linear-gradient(135deg, var(--color-primary), #8b5cf6)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Sparkles size={14} color="#fff" />
            </div>
            <h1 style={{ fontSize: '1.125rem', fontWeight: 800, letterSpacing: '-0.5px' }}>NekoFeed AI</h1>
          </div>
          <button onClick={() => router.push('/search')} style={{ background: 'none', border: 'none', color: 'var(--color-text-main)', cursor: 'pointer', padding: '4px' }}>
            <Search size={22} />
          </button>
        </div>

        <div style={{ display: 'flex', gap: '0' }}>
          {tabs.map(tab => (
            <button
              key={tab}
              onClick={() => handleTabChange(tab)}
              style={{
                flex: 1,
                background: 'none',
                border: 'none',
                fontSize: '0.875rem',
                fontWeight: activeTab === tab ? 700 : 500,
                color: activeTab === tab ? 'var(--color-primary)' : 'var(--color-text-muted)',
                position: 'relative',
                paddingBottom: '12px',
                cursor: 'pointer',
                transition: 'color 0.2s',
                fontFamily: 'inherit',
              }}
            >
              {tab}
              {activeTab === tab && (
                <motion.div
                  layoutId="tab-indicator"
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    left: '30%',
                    right: '30%',
                    height: '3px',
                    backgroundColor: 'var(--color-primary)',
                    borderRadius: '3px 3px 0 0'
                  }}
                  transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                />
              )}
            </button>
          ))}
        </div>
      </header>

      {/* AI 搜索提示条 */}
      <div style={{ padding: '14px 20px 6px' }}>
        <div
          onClick={() => router.push('/search')}
          style={{
            background: 'linear-gradient(135deg, var(--color-primary-soft), rgba(139, 92, 246, 0.08))',
            borderRadius: 'var(--radius-full)',
            padding: '11px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            color: 'var(--color-primary)',
            fontSize: '0.8125rem',
            fontWeight: 500,
            cursor: 'pointer',
            border: '1px solid rgba(92, 77, 255, 0.1)',
            transition: 'all 0.2s',
          }}
        >
          <Sparkles size={16} />
          <span>告诉 AI 你想看什么广告...</span>
        </div>
      </div>

      {/* Active tag filter indicator */}
      {filterTag && (
        <div style={{ padding: '4px 20px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>筛选：</span>
          <span className="tag-chip active" onClick={() => setFilterTag(null)}>{filterTag} ✕</span>
        </div>
      )}

      {/* Pull-to-refresh indicator */}
      {isRefreshing && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '16px' }}>
          <div className="refresh-spinner" />
        </div>
      )}

      {/* 信息流列表 */}
      <main style={{ padding: '8px 16px 0', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {isLoading ? (
          <>
            <SkeletonCard type="large" />
            <SkeletonCard type="small" />
            <SkeletonCard type="large" />
          </>
        ) : filteredAds.length === 0 ? (
          <div className="empty-state">
            <Search size={48} className="icon" />
            <p style={{ fontWeight: 600 }}>暂无相关广告</p>
            <p style={{ fontSize: '0.8125rem' }}>换个频道或标签看看吧</p>
          </div>
        ) : (
          <AnimatePresence mode="popLayout">
            {filteredAds.map((ad, idx) => (
              <div key={ad.id} data-ad-id={ad.id}>
                <AdCard
                  ad={ad}
                  onCardClick={() => handleCardClick(ad.id)}
                  onLike={() => toggleLike(ad.id)}
                  onCollect={() => toggleCollect(ad.id)}
                  onShare={() => handleShare(ad.id)}
                  onTagClick={handleTagClick}
                />
              </div>
            ))}
          </AnimatePresence>
        )}

        {/* Load more indicator */}
        {isLoadingMore && !isLoading && (
          <div className="load-more-trigger">
            <div className="refresh-spinner" />
          </div>
        )}

        {!isLoading && filteredAds.length > 0 && !isLoadingMore && (
          <div className="load-more-trigger" style={{ opacity: 0.5, fontSize: '0.75rem' }}>
            — 已加载全部内容 —
          </div>
        )}
      </main>

      {/* Toast */}
      {toastMsg && <Toast message={toastMsg} onDone={() => setToastMsg(null)} />}
    </div>
  );
}
