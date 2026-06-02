'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import { Heart, Bookmark, Share2, Settings, Bell, Shield, LogOut, ChevronRight, Sparkles, User, Award, Zap } from 'lucide-react';
import { useAdContext, AdItem } from '../context/AdContext';

export default function ProfilePage() {
  const router = useRouter();
  const { ads, toggleLike, toggleCollect } = useAdContext();
  const [activeTab, setActiveTab] = useState<'overview' | 'likes' | 'collections'>('overview');

  // Get liked and collected items
  const likedItems = ads.filter(ad => ad.isLiked);
  const collectedItems = ads.filter(ad => ad.isCollected);

  // Mock user data
  const userData = {
    name: '灵动用户',
    avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200',
    bio: '探索生活的每一个精彩瞬间 ✨',
    level: 'Gold',
    joinedDays: 187,
    stats: {
      likes: likedItems.length,
      collections: collectedItems.length,
      shares: 234,
      exposure: 45600,
    }
  };

  // Recent activity data
  const recentActivity = [
    { id: '1', action: 'liked', adId: '2', time: '3小时前' },
    { id: '2', action: 'collected', adId: '1', time: '1天前' },
    { id: '3', action: 'shared', adId: '3', time: '2天前' },
  ];

  const handleLikeClick = (adId: string) => {
    toggleLike(adId);
  };

  const handleCollectClick = (adId: string) => {
    toggleCollect(adId);
  };

  const renderAdCard = (ad: AdItem, compact: boolean = false) => (
    <motion.div
      key={ad.id}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      className="glass-card"
      style={{
        padding: compact ? '12px' : '16px',
        cursor: 'pointer',
        marginBottom: '12px',
      }}
      onClick={() => router.push(`/ad/${ad.id}`)}
    >
      <div style={{ display: 'flex', gap: '12px' }}>
        <img
          src={ad.imageUrl}
          alt={ad.title}
          style={{
            width: compact ? '64px' : '80px',
            height: compact ? '64px' : '80px',
            borderRadius: 'var(--radius-sm)',
            objectFit: 'cover',
            flexShrink: 0,
          }}
        />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between', minWidth: 0 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
              <span style={{ fontSize: '0.6875rem', fontWeight: 700, color: 'var(--color-primary)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                {ad.channel}
              </span>
              {ad.aiTags[0] && (
                <span style={{ fontSize: '0.6875rem', fontWeight: 600, color: 'var(--color-text-muted)' }}>
                  {ad.aiTags[0]}
                </span>
              )}
            </div>
            <h3 style={{
              fontSize: '0.875rem',
              fontWeight: 600,
              color: 'var(--color-text-main)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}>
              {ad.title}
            </h3>
            <p style={{
              fontSize: '0.75rem',
              color: 'var(--color-text-muted)',
              marginTop: '2px',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}>
              {ad.aiSummary}
            </p>
          </div>
          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
            <button
              onClick={(e) => {
                e.stopPropagation();
                handleLikeClick(ad.id);
              }}
              style={{
                background: ad.isLiked ? 'var(--color-primary-soft)' : 'transparent',
                border: `1px solid ${ad.isLiked ? 'var(--color-primary)' : 'var(--color-bg-secondary)'}`,
                borderRadius: 'var(--radius-full)',
                padding: '4px 8px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '0.7rem',
                color: ad.isLiked ? 'var(--color-primary)' : 'var(--color-text-muted)',
                fontWeight: 600,
              }}
            >
              <Heart size={12} fill={ad.isLiked ? 'currentColor' : 'none'} />
              {ad.likeCount}
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                handleCollectClick(ad.id);
              }}
              style={{
                background: ad.isCollected ? 'var(--color-primary-soft)' : 'transparent',
                border: `1px solid ${ad.isCollected ? 'var(--color-primary)' : 'var(--color-bg-secondary)'}`,
                borderRadius: 'var(--radius-full)',
                padding: '4px 8px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '0.7rem',
                color: ad.isCollected ? 'var(--color-primary)' : 'var(--color-text-muted)',
                fontWeight: 600,
              }}
            >
              <Bookmark size={12} fill={ad.isCollected ? 'currentColor' : 'none'} />
              {ad.collectCount}
            </button>
          </div>
        </div>
      </div>
    </motion.div>
  );

  return (
    <div style={{ minHeight: '100vh', backgroundColor: 'var(--color-bg)', paddingBottom: '80px' }}>
      {/* Header */}
      <div className="glass-panel" style={{
        padding: '20px',
        marginBottom: '20px',
        textAlign: 'center',
        position: 'relative',
      }}>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '16px' }}>
          <button
            onClick={() => router.push('/settings')}
            style={{
              background: 'rgba(92, 77, 255, 0.1)',
              border: 'none',
              borderRadius: 'var(--radius-full)',
              padding: '8px',
              cursor: 'pointer',
              color: 'var(--color-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Settings size={18} />
          </button>
        </div>

        {/* Avatar & User Info */}
        <motion.img
          src={userData.avatar}
          alt={userData.name}
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          style={{
            width: '80px',
            height: '80px',
            borderRadius: 'var(--radius-full)',
            objectFit: 'cover',
            marginBottom: '16px',
            border: '3px solid var(--color-primary)',
          }}
        />

        <h1 style={{
          fontSize: '1.25rem',
          fontWeight: 700,
          color: 'var(--color-text-main)',
          marginBottom: '4px',
        }}>
          {userData.name}
        </h1>

        <p style={{
          fontSize: '0.875rem',
          color: 'var(--color-text-muted)',
          marginBottom: '12px',
        }}>
          {userData.bio}
        </p>

        {/* Badge */}
        <div style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '6px',
          background: 'linear-gradient(135deg, var(--color-accent-orange), var(--color-accent-pink))',
          color: 'white',
          padding: '6px 12px',
          borderRadius: 'var(--radius-full)',
          fontSize: '0.7rem',
          fontWeight: 700,
        }}>
          <Award size={12} />
          {userData.level} 会员
        </div>
      </div>

      {/* Stats Cards */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: '12px',
        padding: '0 20px',
        marginBottom: '24px',
      }}>
        {[
          { icon: Heart, label: '点赞', value: userData.stats.likes, color: 'var(--color-accent-red)' },
          { icon: Bookmark, label: '收藏', value: userData.stats.collections, color: 'var(--color-primary)' },
          { icon: Share2, label: '分享', value: userData.stats.shares, color: 'var(--color-accent-blue)' },
          { icon: Zap, label: '曝光', value: `${(userData.stats.exposure / 1000).toFixed(1)}K`, color: 'var(--color-accent-orange)' },
        ].map((stat, idx) => {
          const Icon = stat.icon;
          return (
            <motion.div
              key={idx}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: idx * 0.08 }}
              className="glass-card"
              style={{
                padding: '16px',
                textAlign: 'center',
              }}
            >
              <div style={{
                width: '40px',
                height: '40px',
                borderRadius: 'var(--radius-sm)',
                background: `${stat.color}20`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 8px',
              }}>
                <Icon size={20} color={stat.color} />
              </div>
              <div style={{ fontSize: '1.125rem', fontWeight: 700, color: 'var(--color-text-main)' }}>
                {stat.value}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '4px' }}>
                {stat.label}
              </div>
            </motion.div>
          );
        })}
      </div>

      {/* Tab Navigation */}
      <div style={{
        display: 'flex',
        gap: '12px',
        padding: '0 20px',
        marginBottom: '20px',
        borderBottom: '1px solid var(--color-bg-secondary)',
      }}>
        {[
          { id: 'overview', label: '概览', icon: Sparkles },
          { id: 'likes', label: `点赞 (${userData.stats.likes})`, icon: Heart },
          { id: 'collections', label: `收藏 (${userData.stats.collections})`, icon: Bookmark },
        ].map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              style={{
                background: 'none',
                border: 'none',
                padding: '12px 0',
                fontSize: '0.875rem',
                fontWeight: isActive ? 700 : 500,
                color: isActive ? 'var(--color-primary)' : 'var(--color-text-muted)',
                borderBottom: isActive ? '2px solid var(--color-primary)' : 'none',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                cursor: 'pointer',
                transition: 'var(--transition-normal)',
              }}
            >
              <Icon size={16} />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Content */}
      <div style={{ padding: '0 20px' }}>
        <AnimatePresence mode="wait">
          {activeTab === 'overview' && (
            <motion.div
              key="overview"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
            >
              {/* AI Stats Card */}
              <div className="glass-card" style={{ padding: '16px', marginBottom: '20px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                  <Sparkles size={16} color="var(--color-primary)" />
                  <span style={{ fontSize: '0.8125rem', fontWeight: 700, color: 'var(--color-primary)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                    AI 洞察
                  </span>
                </div>
                <p style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  你的品味偏向{' '}
                  <span style={{ fontWeight: 600, color: 'var(--color-primary)' }}>科技消费</span> 和{' '}
                  <span style={{ fontWeight: 600, color: 'var(--color-primary)' }}>本地生活</span>。在过去30天中，你的互动热情排名前{' '}
                  <span style={{ fontWeight: 600, color: 'var(--color-accent-orange)' }}>12%</span>。
                </p>
              </div>

              {/* Recent Activity */}
              <h3 style={{ fontSize: '0.875rem', fontWeight: 700, marginBottom: '12px', color: 'var(--color-text-main)' }}>
                最近活动
              </h3>
              <div style={{ marginBottom: '20px' }}>
                {recentActivity.map((activity, idx) => {
                  const actionText = {
                    liked: '点赞了',
                    collected: '收藏了',
                    shared: '分享了',
                  }[activity.action];

                  const adTitle = ads.find(a => a.id === activity.adId)?.title || '未知广告';

                  return (
                    <motion.div
                      key={activity.id}
                      initial={{ opacity: 0, x: -12 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: idx * 0.08 }}
                      className="glass-card"
                      style={{
                        padding: '12px 16px',
                        marginBottom: '8px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                      }}
                    >
                      <div>
                        <p style={{ fontSize: '0.875rem', color: 'var(--color-text-main)', marginBottom: '4px' }}>
                          {actionText} <span style={{ fontWeight: 700 }}>{adTitle.slice(0, 12)}...</span>
                        </p>
                        <p style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>
                          {activity.time}
                        </p>
                      </div>
                      <ChevronRight size={16} color="var(--color-text-muted)" />
                    </motion.div>
                  );
                })}
              </div>

              {/* Settings */}
              <h3 style={{ fontSize: '0.875rem', fontWeight: 700, marginBottom: '12px', color: 'var(--color-text-main)' }}>
                设置与隐私
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {[
                  { icon: Bell, label: '通知设置' },
                  { icon: Shield, label: '隐私与安全' },
                  { icon: LogOut, label: '退出登录' },
                ].map((item, idx) => {
                  const Icon = item.icon;
                  return (
                    <motion.button
                      key={idx}
                      initial={{ opacity: 0, x: -12 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 0.24 + idx * 0.08 }}
                      className="glass-card"
                      style={{
                        padding: '12px 16px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        border: 'none',
                        background: 'var(--color-surface)',
                        cursor: 'pointer',
                        fontSize: '0.875rem',
                        fontWeight: 500,
                        color: 'var(--color-text-main)',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <Icon size={18} color="var(--color-primary)" />
                        {item.label}
                      </div>
                      <ChevronRight size={16} color="var(--color-text-muted)" />
                    </motion.button>
                  );
                })}
              </div>
            </motion.div>
          )}

          {activeTab === 'likes' && (
            <motion.div
              key="likes"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
            >
              {likedItems.length > 0 ? (
                likedItems.map(ad => renderAdCard(ad))
              ) : (
                <div style={{ textAlign: 'center', padding: '40px 20px' }}>
                  <Heart size={40} color="var(--color-text-muted)" opacity={0.3} style={{ margin: '0 auto 16px' }} />
                  <p style={{ color: 'var(--color-text-muted)', fontSize: '0.875rem' }}>
                    还没有点赞任何内容
                  </p>
                </div>
              )}
            </motion.div>
          )}

          {activeTab === 'collections' && (
            <motion.div
              key="collections"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
            >
              {collectedItems.length > 0 ? (
                collectedItems.map(ad => renderAdCard(ad))
              ) : (
                <div style={{ textAlign: 'center', padding: '40px 20px' }}>
                  <Bookmark size={40} color="var(--color-text-muted)" opacity={0.3} style={{ margin: '0 auto 16px' }} />
                  <p style={{ color: 'var(--color-text-muted)', fontSize: '0.875rem' }}>
                    还没有收藏任何内容
                  </p>
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
