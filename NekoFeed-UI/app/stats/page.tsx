'use client';

import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { ChevronLeft, Eye, MousePointer, Heart, Bookmark, Share2, TrendingUp, BarChart3 } from 'lucide-react';
import { useAdContext } from '../context/AdContext';

function formatNum(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + '万';
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
  return n.toString();
}

export default function StatsPage() {
  const router = useRouter();
  const { getStats } = useAdContext();
  const stats = getStats();
  const maxExposure = stats.ranking[0]?.exposureCount || 1;

  const statCards = [
    { label: '总曝光', value: formatNum(stats.totalExposure), icon: Eye, color: 'purple' },
    { label: '总点击', value: formatNum(stats.totalClicks), icon: MousePointer, color: 'blue' },
    { label: '总点赞', value: formatNum(stats.totalLikes), icon: Heart, color: 'red' },
    { label: '总收藏', value: formatNum(stats.totalCollects), icon: Bookmark, color: 'orange' },
    { label: '总分享', value: formatNum(stats.totalShares), icon: Share2, color: 'green' },
    { label: 'CTR', value: stats.ctr.toFixed(1) + '%', icon: TrendingUp, color: 'pink' },
  ];

  return (
    <div style={{ minHeight: '100vh', backgroundColor: 'var(--color-bg)', paddingBottom: '40px' }}>
      {/* Header */}
      <header className="glass-panel" style={{
        position: 'sticky',
        top: 0,
        zIndex: 20,
        padding: '14px 20px',
        display: 'flex',
        alignItems: 'center',
        gap: '12px'
      }}>
        <button
          onClick={() => router.back()}
          style={{ background: 'none', border: 'none', color: 'var(--color-text-main)', cursor: 'pointer', padding: '4px' }}
        >
          <ChevronLeft size={24} />
        </button>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <BarChart3 size={18} color="var(--color-primary)" />
          <h1 style={{ fontSize: '1.0625rem', fontWeight: 700 }}>数据统计</h1>
        </div>
      </header>

      {/* Stat Overview Cards */}
      <div style={{ padding: '20px 20px 0' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          {statCards.map((card, idx) => {
            const Icon = card.icon;
            return (
              <motion.div
                key={card.label}
                initial={{ opacity: 0, y: 16, scale: 0.95 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={{ delay: idx * 0.06, duration: 0.35 }}
                className={`stat-card ${card.color}`}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <div className="stat-label">{card.label}</div>
                    <div className="stat-value">{card.value}</div>
                  </div>
                  <Icon size={22} style={{ opacity: 0.6 }} />
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>

      {/* Ranking List */}
      <div style={{ padding: '24px 20px 0' }}>
        <h2 style={{ fontSize: '1.0625rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <TrendingUp size={18} color="var(--color-primary)" />
          广告排行榜
        </h2>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {stats.ranking.map((ad, idx) => (
            <motion.div
              key={ad.id}
              initial={{ opacity: 0, x: -16 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.3 + idx * 0.05 }}
              className="glass-card"
              style={{ padding: '14px 16px', cursor: 'pointer' }}
              onClick={() => router.push(`/ad/${ad.id}`)}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                {/* Rank number */}
                <div style={{
                  width: '28px',
                  height: '28px',
                  borderRadius: 'var(--radius-sm)',
                  background: idx < 3 ? 'linear-gradient(135deg, var(--color-primary), #8b5cf6)' : 'var(--color-bg-secondary)',
                  color: idx < 3 ? '#fff' : 'var(--color-text-muted)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.8125rem',
                  fontWeight: 800,
                  flexShrink: 0,
                }}>
                  {idx + 1}
                </div>

                {/* Thumbnail */}
                <div style={{ width: '44px', height: '44px', borderRadius: 'var(--radius-xs)', overflow: 'hidden', flexShrink: 0 }}>
                  <img src={ad.imageUrl} alt={ad.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
                </div>

                {/* Info */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <h3 style={{ fontSize: '0.8125rem', fontWeight: 600, marginBottom: '4px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{ad.title}</h3>

                  <div style={{ display: 'flex', gap: '12px', fontSize: '0.6875rem', color: 'var(--color-text-muted)', marginBottom: '6px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}><Eye size={11} /> {formatNum(ad.exposureCount)}</span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}><MousePointer size={11} /> {formatNum(ad.clickCount)}</span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}><Heart size={11} /> {formatNum(ad.likeCount)}</span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}><Bookmark size={11} /> {formatNum(ad.collectCount)}</span>
                  </div>

                  {/* Bar */}
                  <div className="rank-bar-bg">
                    <motion.div
                      className="rank-bar-fill"
                      initial={{ width: 0 }}
                      animate={{ width: `${(ad.exposureCount / maxExposure) * 100}%` }}
                      transition={{ delay: 0.5 + idx * 0.05, duration: 0.6 }}
                    />
                  </div>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </div>
  );
}
