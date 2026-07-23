import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { REVIEW_URL, authHeaders, hasToken } from '../api'
import { TAB_COLORS } from '../theme'

const ACCENT = TAB_COLORS.navigation.accent
const ACCENT_DARK = TAB_COLORS.navigation.dark
const ACCENT_TINT = TAB_COLORS.navigation.tint

// SM-2 quality 매핑: 사용자에게는 4단계로 물어보고 내부적으로 0~5점으로 변환한다.
const GRADES = [
  { label: '다시', quality: 2, color: '#ef4444', hint: '전혀 기억 안 남' },
  { label: '어려움', quality: 3, color: '#f59e0b', hint: '겨우 기억함' },
  { label: '좋음', quality: 4, color: '#10b981', hint: '무난하게 기억함' },
  { label: '쉬움', quality: 5, color: '#3b82f6', hint: '아주 쉬웠음' },
]

function speak(text) {
  if (!text || !('speechSynthesis' in window)) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'ko-KR'
  utterance.rate = 0.85
  window.speechSynthesis.speak(utterance)
}

// nextReviewAt(ISO 문자열)을 사람이 읽기 좋은 한국어 상대 표현으로 바꾼다.
function formatWhen(iso) {
  if (!iso) return ''
  const target = new Date(iso)
  const now = new Date()
  const diffMs = target - now
  if (diffMs <= 0) return '지금'
  const diffMin = Math.round(diffMs / 60000)
  if (diffMin < 60) return `${diffMin}분 후`
  const diffHour = Math.round(diffMin / 60)
  if (diffHour < 24 && target.getDate() === now.getDate()) {
    return `오늘 ${String(target.getHours()).padStart(2, '0')}:${String(target.getMinutes()).padStart(2, '0')}`
  }
  const diffDay = Math.ceil(diffMs / 86400000)
  if (diffDay === 1) return '내일'
  return `${diffDay}일 후`
}

/**
 * 복습 알람 단계.
 * - 방금 배운 문장을 복습 목록(간격 반복/SM-2)에 저장한다.
 * - 지금 복습할 문장(알람 대상)이 있으면 플래시카드로 복습 세션을 진행한다.
 * - 복습 결과(다시/어려움/좋음/쉬움)에 따라 다음 알람 시각이 자동으로 정해진다.
 */
function ReviewAlarm({ justLearned, onComplete, onRequireAuth }) {
  const [status, setStatus] = useState('loading') // loading | need-login | ready | error
  const [error, setError] = useState('')
  const [savedInfo, setSavedInfo] = useState(null) // 방금 저장된 항목(다음 복습 시각 안내용)
  const [upcomingCount, setUpcomingCount] = useState(0)

  // 복습 세션 상태
  const [queue, setQueue] = useState([]) // 이번 세션에서 복습할 카드들
  const [index, setIndex] = useState(0)
  const [answer, setAnswer] = useState('')
  const [revealed, setRevealed] = useState(false)
  const [grading, setGrading] = useState(false)
  const [reviewedCount, setReviewedCount] = useState(0)

  const startedRef = useRef(false)

  useEffect(() => {
    if (startedRef.current) return
    startedRef.current = true

    if (!hasToken()) {
      setStatus('need-login')
      return
    }

    const init = async () => {
      try {
        // 1) 방금 배운 문장을 복습 목록에 추가 (있을 때만)
        if (justLearned && justLearned.sentence) {
          const saveRes = await axios.post(
            `${REVIEW_URL}/items`,
            {
              interest: justLearned.interest,
              sentence: justLearned.sentence,
              meaning: justLearned.meaning,
              pattern: justLearned.pattern,
              sensoryWord: justLearned.sensoryWord,
            },
            { headers: authHeaders({ 'Content-Type': 'application/json; charset=utf-8' }) },
          )
          setSavedInfo(saveRes.data?.data ?? null)
        }

        // 2) 지금 복습할(알람 대상) 문장 불러오기
        const dueRes = await axios.get(`${REVIEW_URL}/due`, { headers: authHeaders() })
        const due = dueRes.data?.data ?? []

        // 3) 예정 개수 파악 (전체 - due)
        const allRes = await axios.get(`${REVIEW_URL}/items`, { headers: authHeaders() })
        const all = allRes.data?.data ?? []
        setUpcomingCount(Math.max(0, all.length - due.length))

        setQueue(due)
        setStatus('ready')
      } catch (err) {
        if (err.response?.status === 401) {
          setStatus('need-login')
        } else {
          setError(err.response?.data?.message || '복습 정보를 불러오지 못했어요.')
          setStatus('error')
        }
      }
    }
    init()
  }, [justLearned])

  const startEarlyReview = async () => {
    try {
      const allRes = await axios.get(`${REVIEW_URL}/items`, { headers: authHeaders() })
      const all = allRes.data?.data ?? []
      if (all.length === 0) return
      setQueue(all)
      setIndex(0)
      setAnswer('')
      setRevealed(false)
    } catch (err) {
      setError(err.response?.data?.message || '복습 항목을 불러오지 못했어요.')
    }
  }

  const handleGrade = async (grade) => {
    const card = queue[index]
    if (!card || grading) return
    setGrading(true)
    try {
      await axios.post(
        `${REVIEW_URL}/items/${card.id}/grade`,
        { quality: grade.quality },
        { headers: authHeaders({ 'Content-Type': 'application/json; charset=utf-8' }) },
      )
      setReviewedCount((c) => c + 1)

      // "다시"(실패)면 이번 세션 맨 뒤에 다시 세워 반복 학습시킨다.
      let nextQueue = queue
      if (grade.quality < 3) {
        nextQueue = [...queue, card]
        setQueue(nextQueue)
      }
      setIndex((i) => i + 1)
      setAnswer('')
      setRevealed(false)
    } catch (err) {
      setError(err.response?.data?.message || '복습 결과 저장에 실패했어요.')
    } finally {
      setGrading(false)
    }
  }

  // ----- 렌더링 -----
  const box = { border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }

  if (status === 'loading') {
    return <div style={box}>복습 정보를 불러오는 중...</div>
  }

  if (status === 'need-login') {
    return (
      <div style={box}>
        <h3 style={{ marginTop: 0 }}>🔔 복습 알람은 회원 전용이에요</h3>
        <p style={{ color: '#666' }}>
          복습 알람은 배운 문장을 <b>망각곡선(간격 반복)</b>에 따라 다시 꺼내주는 기능이에요.
          어디서 어떤 문장을 배웠는지 기억해 두려면 계정이 필요해요.
        </p>
        <p style={{ color: '#999', fontSize: '14px' }}>가입은 몇 초면 끝나고, 바로 복습이 시작됩니다.</p>
        {onRequireAuth && (
          <button type="button" onClick={onRequireAuth} style={primaryBtn}>
            로그인 / 회원가입하고 복습 시작하기
          </button>
        )}
        {onComplete && (
          <button
            type="button"
            onClick={onComplete}
            style={{ ...primaryBtn, backgroundColor: '#fff', color: '#666', border: '1px solid #ddd', marginTop: '8px' }}
          >
            지금은 건너뛰기 →
          </button>
        )}
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div style={box}>
        <p style={{ color: '#dc3545' }}>⚠ {error}</p>
        {onComplete && (
          <button type="button" onClick={onComplete} style={primaryBtn}>
            학습 완료 🎉
          </button>
        )}
      </div>
    )
  }

  const sessionDone = index >= queue.length
  const card = sessionDone ? null : queue[index]

  return (
    <div style={box}>
      <h3 style={{ marginTop: 0 }}>🔔 복습 알람</h3>

      {/* 방금 배운 문장 저장 안내 */}
      {savedInfo && (
        <div style={{
          padding: '14px', borderRadius: '10px', backgroundColor: ACCENT_TINT,
          border: '1px solid #fde68a', marginBottom: '16px', fontSize: '14px',
        }}>
          방금 배운 문장 <b>“{savedInfo.sentence}”</b> 을(를) 복습 목록에 넣었어요.<br />
          망각곡선에 따라 <b>{formatWhen(savedInfo.nextReviewAt)}</b> 에 다시 알려드릴게요.
        </div>
      )}

      {error && <p style={{ color: '#dc3545' }}>⚠ {error}</p>}

      {/* 복습할 카드가 있을 때: 플래시카드 세션 */}
      {!sessionDone && card && (
        <>
          <div style={{ fontSize: '13px', color: '#999', marginBottom: '10px' }}>
            지금 복습할 문장 {queue.length - index}개 남음
          </div>

          <div style={{
            fontSize: '22px', fontWeight: 700, padding: '20px', borderRadius: '12px',
            backgroundColor: '#f9fafb', marginBottom: '14px',
            display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap',
          }}>
            {card.sentence}
            <button
              type="button"
              onClick={() => speak(card.sentence)}
              style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: '20px' }}
              aria-label="문장 듣기"
            >
              🔊
            </button>
          </div>

          {!revealed ? (
            <>
              <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
                이 문장의 뜻을 떠올려 적어보세요
              </label>
              <textarea
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                placeholder="기억나는 뜻을 적어보세요..."
                style={{ width: '100%', padding: '10px', fontSize: '15px', minHeight: '70px', marginBottom: '14px' }}
              />
              <button type="button" onClick={() => setRevealed(true)} style={primaryBtn}>
                확인하기
              </button>
            </>
          ) : (
            <>
              <div style={{ padding: '16px', borderRadius: '12px', backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', marginBottom: '14px' }}>
                <div style={{ fontWeight: 700, marginBottom: '6px' }}>정답: {card.meaning}</div>
                {card.pattern && <div style={{ fontSize: '13px', color: '#666' }}>패턴: {card.pattern}</div>}
                {answer.trim() && (
                  <div style={{ fontSize: '13px', color: '#999', marginTop: '8px' }}>
                    내가 적은 답: {answer}
                  </div>
                )}
              </div>

              <div style={{ fontSize: '13px', color: '#999', marginBottom: '8px' }}>
                얼마나 잘 기억했나요? (다음 복습 간격이 자동으로 정해져요)
              </div>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {GRADES.map((g) => (
                  <button
                    key={g.label}
                    type="button"
                    onClick={() => handleGrade(g)}
                    disabled={grading}
                    title={g.hint}
                    style={{
                      flex: '1 1 0', minWidth: '70px', padding: '12px 8px', cursor: grading ? 'not-allowed' : 'pointer',
                      backgroundColor: '#fff', color: g.color, border: `2px solid ${g.color}`,
                      borderRadius: '10px', fontSize: '14px', fontWeight: 700,
                    }}
                  >
                    {g.label}
                  </button>
                ))}
              </div>
            </>
          )}
        </>
      )}

      {/* 지금 복습할 카드가 없을 때 */}
      {sessionDone && (
        <div style={{ textAlign: 'center', padding: '10px 0' }}>
          {reviewedCount > 0 ? (
            <>
              <div style={{ fontSize: '40px', marginBottom: '8px' }}>🎉</div>
              <p style={{ fontWeight: 700, marginBottom: '4px' }}>복습 완료! {reviewedCount}개 문장을 복습했어요.</p>
            </>
          ) : (
            <p style={{ color: '#666' }}>
              지금 당장 복습할 문장은 없어요.{upcomingCount > 0 && <> 예정된 복습 <b>{upcomingCount}개</b>가 있어요.</>}
            </p>
          )}
          {upcomingCount > 0 && (
            <button type="button" onClick={startEarlyReview} style={{ ...secondaryBtn, marginBottom: '10px' }}>
              🔁 미리 복습해보기
            </button>
          )}
        </div>
      )}

      {onComplete && (
        <button type="button" onClick={onComplete} style={{ ...primaryBtn, marginTop: '16px' }}>
          학습 완료 🎉
        </button>
      )}
    </div>
  )
}

const primaryBtn = {
  width: '100%', padding: '14px', fontSize: '16px', cursor: 'pointer',
  backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '8px',
}

const secondaryBtn = {
  width: '100%', padding: '12px', fontSize: '15px', cursor: 'pointer',
  backgroundColor: '#fff', color: ACCENT_DARK, border: `1px solid ${ACCENT}`, borderRadius: '8px',
}

export default ReviewAlarm
