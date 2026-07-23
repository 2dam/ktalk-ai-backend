import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { AI_URL } from '../api'
import { TAB_COLORS } from '../theme'

const ACCENT = TAB_COLORS.navigation.accent
const ACCENT_DARK = TAB_COLORS.navigation.dark
const ACCENT_TINT = TAB_COLORS.navigation.tint

// 장르별 검색 보조어. 관심사/핵심어에 붙여서 실제 한국어가 쓰이는 클립을 찾는다.
const GENRES = [
  { id: 'kpop', label: '🎵 K-POP', suffix: '가사', hint: '노래 속에서 이 표현 만나기' },
  { id: 'drama', label: '🎬 드라마', suffix: '드라마 명장면', hint: '드라마 대사 속 실제 사용' },
]

function speak(text) {
  if (!text || !('speechSynthesis' in window)) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'ko-KR'
  utterance.rate = 0.9
  window.speechSynthesis.speak(utterance)
}

// 문장/단어에서 검색에 쓸 핵심어를 고른다.
function pickKeyword(lesson) {
  if (!lesson) return '한국어'
  return (
    (lesson.sensoryWord && lesson.sensoryWord.trim()) ||
    (lesson.vocab && lesson.vocab[0] && lesson.vocab[0].word) ||
    (lesson.interest && lesson.interest.trim()) ||
    '한국어'
  )
}

/**
 * 원어민 실사용(Desire) 단계.
 * 방금 뜻을 유추한 표현을, K-POP·드라마처럼 학습자가 동경하는 실제 콘텐츠 속에서
 * "원어민은 이렇게 쓴다"로 보여줘 "나도 이렇게 말하고 싶다"는 욕구를 만든다.
 * (별도 백엔드 없이 기존 /api/ai/videos/search 재사용)
 */
function NativeUsage({ lesson, onNext }) {
  const keyword = pickKeyword(lesson)
  const [genre, setGenre] = useState('kpop')
  const [query, setQuery] = useState(`${keyword} ${GENRES[0].suffix}`)
  const [videos, setVideos] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [watchedCount, setWatchedCount] = useState(0)
  const didInit = useRef(false)

  const runSearch = async (q) => {
    const term = (q ?? query).trim()
    if (!term) return
    setLoading(true)
    setError('')
    try {
      const res = await axios.get(`${AI_URL}/videos/search`, {
        params: { query: term, maxResults: 6, preferShort: true, keyword },
      })
      setVideos(res.data?.data || [])
    } catch (err) {
      setError(err.response?.data?.message || '영상을 불러오지 못했어요. YouTube API 키 설정을 확인해주세요.')
      setVideos([])
    } finally {
      setLoading(false)
    }
  }

  // 진입 시 1회 자동 검색
  useEffect(() => {
    if (didInit.current) return
    didInit.current = true
    runSearch(query)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const switchGenre = (g) => {
    if (g.id === genre) return
    setGenre(g.id)
    const nextQuery = `${keyword} ${g.suffix}`
    setQuery(nextQuery)
    runSearch(nextQuery)
  }

  const openVideo = (video) => {
    setWatchedCount((c) => c + 1)
    window.open(`https://www.youtube.com/watch?v=${video.videoId}`, '_blank', 'noopener,noreferrer')
  }

  return (
    <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
      <h3 style={{ marginTop: 0 }}>원어민은 이렇게 써요 🎤</h3>
      <p style={{ color: '#666', marginTop: 0 }}>
        방금 뜻을 짐작해본 이 표현, 실제로 K-POP과 드라마 속 한국인들은 이렇게 씁니다.
        몇 개만 봐도 귀가 트이고, 곧 <b>당신도 이렇게 말하게</b> 돼요.
      </p>

      {/* 목표 문장 다시 상기 + 듣기 */}
      {lesson && (
        <div style={{
          fontSize: '18px', fontWeight: 700, padding: '16px', borderRadius: '12px',
          backgroundColor: ACCENT_TINT, marginBottom: '16px',
          display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap',
        }}>
          {lesson.sentence}
          <button
            type="button"
            onClick={() => speak(lesson.sentence)}
            style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: '18px' }}
            aria-label="문장 듣기"
          >
            🔊
          </button>
          {lesson.meaning && (
            <span style={{ color: '#999', fontSize: '13px', fontWeight: 400 }}>({lesson.meaning})</span>
          )}
        </div>
      )}

      {/* 장르 토글 */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
        {GENRES.map((g) => {
          const active = g.id === genre
          return (
            <button
              key={g.id}
              type="button"
              onClick={() => switchGenre(g)}
              style={{
                flex: 1, padding: '10px', cursor: 'pointer', borderRadius: '10px',
                border: '2px solid ' + (active ? ACCENT : '#e5e7eb'),
                backgroundColor: active ? ACCENT_TINT : '#fff',
                color: active ? ACCENT_DARK : '#666', fontWeight: active ? 700 : 400, fontSize: '15px',
              }}
            >
              {g.label}
              <div style={{ fontSize: '11px', color: '#999', fontWeight: 400, marginTop: '2px' }}>{g.hint}</div>
            </button>
          )
        })}
      </div>

      {/* 검색어 (수정 가능) */}
      <form
        onSubmit={(e) => { e.preventDefault(); runSearch() }}
        style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}
      >
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="검색어를 바꿔 다른 클립을 찾아보세요"
          style={{ flex: 1, padding: '10px', fontSize: '14px' }}
        />
        <button
          type="submit"
          disabled={loading}
          style={{
            padding: '10px 18px', cursor: loading ? 'not-allowed' : 'pointer',
            backgroundColor: loading ? '#ccc' : ACCENT, color: 'white', border: 'none', borderRadius: '8px',
          }}
        >
          {loading ? '찾는 중…' : '검색'}
        </button>
      </form>

      {error && <p style={{ color: '#dc3545' }}>⚠ {error}</p>}

      {/* 클립 결과 */}
      {loading && videos.length === 0 ? (
        <p style={{ color: '#999' }}>클립을 불러오는 중…</p>
      ) : videos.length > 0 ? (
        <>
          {videos.every((v) => !v.matched) && (
            <p style={{ fontSize: '12px', color: '#999', marginTop: 0, marginBottom: '10px' }}>
              "{keyword}"이(가) 정확히 나오는 클립은 못 찾아서, 관련 있을 만한 영상을 대신 보여드려요.
            </p>
          )}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '14px' }}>
            {videos.map((video) => (
              <div
                key={video.videoId}
                onClick={() => openVideo(video)}
                style={{ border: '1px solid #e5e7eb', borderRadius: '10px', overflow: 'hidden', cursor: 'pointer' }}
              >
                {video.thumbnailUrl && (
                  <div style={{ position: 'relative' }}>
                    <img src={video.thumbnailUrl} alt={video.title} style={{ width: '100%', display: 'block' }} />
                    <div style={{
                      position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
                      width: '46px', height: '46px', borderRadius: '50%', backgroundColor: 'rgba(0,0,0,0.65)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}>
                      <div style={{
                        width: 0, height: 0, marginLeft: '3px',
                        borderTop: '10px solid transparent', borderBottom: '10px solid transparent',
                        borderLeft: '16px solid white',
                      }} />
                    </div>
                    {!video.matched && (
                      <span style={{
                        position: 'absolute', top: '6px', left: '6px', padding: '2px 8px',
                        borderRadius: '999px', backgroundColor: 'rgba(0,0,0,0.65)', color: '#fff', fontSize: '10px',
                      }}>
                        관련 영상
                      </span>
                    )}
                  </div>
                )}
                <div style={{ padding: '10px' }}>
                  <div style={{ fontWeight: 700, fontSize: '13px', lineHeight: 1.3 }}>{video.title}</div>
                  <div style={{ fontSize: '12px', color: '#666', marginTop: '4px' }}>{video.channelName}</div>
                </div>
              </div>
            ))}
          </div>
        </>
      ) : (
        !loading && <p style={{ color: '#999' }}>클립이 없어요. 검색어를 바꿔보세요.</p>
      )}

      <p style={{ fontSize: '12px', color: '#999', marginTop: '10px' }}>
        썸네일을 누르면 유튜브에서 영상이 열립니다. {watchedCount > 0 && `· ${watchedCount}개 감상함`}
      </p>

      {/* 다음 단계 CTA (Action으로 연결) */}
      <button
        type="button"
        onClick={onNext}
        style={{
          width: '100%', padding: '14px', fontSize: '16px', cursor: 'pointer',
          backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '8px', marginTop: '16px',
        }}
      >
        나도 이렇게 말해보기 → 패턴 응용
      </button>
    </div>
  )
}

export default NativeUsage
