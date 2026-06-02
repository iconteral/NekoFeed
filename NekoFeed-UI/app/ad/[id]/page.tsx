'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { Heart, Bookmark, Share2, ChevronLeft, Sparkles, ExternalLink, Eye, MousePointer, Volume2, VolumeX } from 'lucide-react';
import { useAdContext } from '../../context/AdContext';

/* ─── Toast ─── */
function Toast({ message, onDone }: { message: string; onDone: () => void }) {
  const [exiting, setExiting] = useState(false);
  useEffect(() => {
    const t1 = setTimeout(() => setExiting(true), 1800);
    const t2 = setTimeout(onDone, 2100);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [onDone]);
  return <div className={`toast ${exiting ? 'exit' : ''}`}>{message}</div>;
}

export default function AdDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const { getAdById, toggleLike, toggleCollect, incrementShare, incrementClick } = useAdContext();
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [videoMuted, setVideoMuted] = useState(true);
  const [resolvedId, setResolvedId] = useState<string | null>(null);

  useEffect(() => {
    params.then(p => {
      setResolvedId(p.id);
      incrementClick(p.id);
    });
  }, [params, incrementClick]);

  const ad = resolvedId ? getAdById(resolvedId) : undefined;

  const handleShare = useCallback(() => {
    if (ad) {
      incrementShare(ad.id);
      setToastMsg('链接已复制，快去分享吧 🎉');
    }
  }, [ad, incrementShare]);

  if (!ad) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--color-bg)' }}>
        <div className="refresh-spinner" />
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', backgroundColor: 'var(--color-bg)', display: 'flex', flexDirection: 'column' }}>

      {/* 顶部透明导航 */}
      <header style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 20,
        padding: '14px 20px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
      }}>
        <motion.button
          whileTap={{ scale: 0.9 }}
          onClick={() => router.back()}
          style={{
            background: 'rgba(0,0,0,0.35)',
            backdropFilter: 'blur(12px)',
            border: 'none',
            color: '#fff',
            width: '38px',
            height: '38px',
            borderRadius: 'var(--radius-full)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer'
          }}
        >
          <ChevronLeft size={22} />
        </motion.button>
      </header>

      {/* Hero Media */}
      <div style={{ width: '100%', height: '380px', position: 'relative' }}>
        {ad.type === 'VIDEO' ? (
          <>
            <video
              src={ad.videoUrl}
              poster={ad.imageUrl}
              autoPlay
              muted={videoMuted}
              loop
              playsInline
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
            <button
              onClick={() => setVideoMuted(!videoMuted)}
              style={{ position: 'absolute', bottom: '60px', right: '16px', background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(8px)', border: 'none', borderRadius: 'var(--radius-full)', width: '36px', height: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', cursor: 'pointer', zIndex: 5 }}
            >
              {videoMuted ? <VolumeX size={16} /> : <Volume2 size={16} />}
            </button>
          </>
        ) : (
          <img src={ad.imageUrl} alt={ad.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        )}
        <div style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          height: '120px',
          background: 'linear-gradient(to top, var(--color-bg) 0%, transparent 100%)'
        }} />
      </div>

      {/* Detail Content */}
      <motion.main
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1 }}
        style={{ padding: '0 20px 120px 20px', marginTop: '-28px', position: 'relative', zIndex: 10 }}
      >

        {/* Title & Brand */}
        <div style={{ marginBottom: '20px' }}>
          <div style={{ fontSize: '0.8125rem', color: 'var(--color-primary)', marginBottom: '8px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: 'var(--color-primary)' }} />
            {ad.brand} · 赞助
          </div>
          <h1 style={{ fontSize: '1.375rem', fontWeight: 800, lineHeight: 1.3, letterSpacing: '-0.3px' }}>{ad.title}</h1>
        </div>

        {/* Stats mini bar */}
        <div style={{ display: 'flex', gap: '16px', marginBottom: '20px', fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><Eye size={14} /> {ad.exposureCount} 曝光</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><MousePointer size={14} /> {ad.clickCount} 点击</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><Heart size={14} /> {ad.likeCount}</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><Bookmark size={14} /> {ad.collectCount}</span>
        </div>

        {/* AI 智能总结模块 */}
        <div className="glass-card" style={{ padding: '18px', marginBottom: '20px', border: '1px solid rgba(92, 77, 255, 0.12)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px' }}>
            <div style={{ width: '24px', height: '24px', borderRadius: 'var(--radius-xs)', background: 'linear-gradient(135deg, var(--color-primary), #8b5cf6)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Sparkles size={13} color="#fff" />
            </div>
            <h3 style={{ fontSize: '0.9375rem', fontWeight: 700, color: 'var(--color-primary)' }}>AI 智能总结</h3>
          </div>
          <p style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)', lineHeight: 1.6, marginBottom: '14px' }}>
            {ad.aiSummary}
          </p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {ad.aiTags.map(tag => (
              <span key={tag} className="tag-chip default">{tag}</span>
            ))}
          </div>
        </div>

        {/* Product Description */}
        <div>
          <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '10px' }}>产品介绍</h3>
          <p style={{ fontSize: '0.875rem', color: 'var(--color-text-muted)', lineHeight: 1.7 }}>
            {ad.originalDescription}
          </p>
        </div>
      </motion.main>

      {/* Bottom Action Bar */}
      <div className="glass-panel" style={{
        position: 'fixed',
        bottom: 0,
        left: '50%',
        transform: 'translateX(-50%)',
        width: '100%',
        maxWidth: '480px',
        padding: '12px 20px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '12px',
        borderTop: '1px solid rgba(0,0,0,0.05)',
        zIndex: 30
      }}>
        <div style={{ display: 'flex', gap: '20px' }}>
          <motion.button
            whileTap={{ scale: 0.85 }}
            onClick={() => toggleLike(ad.id)}
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2px', background: 'none', border: 'none', color: ad.isLiked ? 'var(--color-accent-red)' : 'var(--color-text-main)', cursor: 'pointer', fontFamily: 'inherit' }}
          >
            <Heart size={22} fill={ad.isLiked ? 'currentColor' : 'none'} />
            <span style={{ fontSize: '0.5625rem', fontWeight: 500 }}>{ad.isLiked ? '已赞' : '点赞'}</span>
          </motion.button>
          <motion.button
            whileTap={{ scale: 0.85 }}
            onClick={() => toggleCollect(ad.id)}
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2px', background: 'none', border: 'none', color: ad.isCollected ? 'var(--color-accent-orange)' : 'var(--color-text-main)', cursor: 'pointer', fontFamily: 'inherit' }}
          >
            <Bookmark size={22} fill={ad.isCollected ? 'currentColor' : 'none'} />
            <span style={{ fontSize: '0.5625rem', fontWeight: 500 }}>{ad.isCollected ? '已收藏' : '收藏'}</span>
          </motion.button>
          <motion.button
            whileTap={{ scale: 0.85 }}
            onClick={handleShare}
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2px', background: 'none', border: 'none', color: 'var(--color-text-main)', cursor: 'pointer', fontFamily: 'inherit' }}
          >
            <Share2 size={22} />
            <span style={{ fontSize: '0.5625rem', fontWeight: 500 }}>分享</span>
          </motion.button>
        </div>

        <motion.button
          whileTap={{ scale: 0.96 }}
          style={{
            flex: 1,
            background: 'linear-gradient(135deg, var(--color-primary), #8b5cf6)',
            color: '#fff',
            border: 'none',
            padding: '13px 0',
            borderRadius: 'var(--radius-full)',
            fontSize: '0.875rem',
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '6px',
            cursor: 'pointer',
            boxShadow: 'var(--shadow-glow)',
            fontFamily: 'inherit',
          }}
        >
          <span>查看详情</span>
          <ExternalLink size={14} />
        </motion.button>
      </div>

      {/* Toast */}
      {toastMsg && <Toast message={toastMsg} onDone={() => setToastMsg(null)} />}
    </div>
  );
}
