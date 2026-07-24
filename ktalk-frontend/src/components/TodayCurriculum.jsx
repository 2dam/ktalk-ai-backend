import { useEffect, useState } from 'react'
import axios from 'axios'
import { API_BASE, authHeaders, hasToken } from '../api'
import { TAB_COLORS } from '../theme'
import ClickableKorean from './ClickableKorean'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`

const ACCENT = TAB_COLORS.navigation.accent
const ACCENT_DARK = TAB_COLORS.navigation.dark
const ACCENT_TINT = TAB_COLORS.navigation.tint

const NEEDS_ASSESSMENT_MESSAGE = '먼저 학습 유형 진단을 완료해주세요.'

function TodayCurriculum({ onBack, onRequireAuth, onGoToAssessment }) {
  const loggedIn = hasToken()

  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [completing, setCompleting] = useState(false)

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await axios.get(`${CURRICULUM_URL}/today`, { headers: authHeaders() })
      if (res.data?.success) {
        setData(res.data.data)
      } else {
        setError(res.data?.message || '커리큘럼을 불러오지 못했어요.')
      }
    } catch (err) {
      setError(err.response?.data?.message || '커리큘럼을 불러오지 못했어요.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!loggedIn) {
      setLoading(false)
      return
    }
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleComplete = async () => {
    setCompleting(true)
    try {
      const res = await axios.post(`${CURRICULUM_URL}/complete`, null, { headers: authHeaders() })
      if (res.data?.success) {
        setData(res.data.data)
      } else {
        setError(res.data?.message || '완료 처리에 실패했어요.')
      }
    } catch (err) {
      setError(err.response?.data?.message || '완료 처리에 실패했어요.')
    } finally {
      setCompleting(false)
    }
  }

  if (!loggedIn) {
    return (
      <main className="topik-page" id="top">
        <div className="topik-page-head">
          <button type="button" className="topik-back" onClick={onBack}>← TOPIK 메뉴로</button>
          <span className="topik-badge">TOPIK 코스</span>
          <h1>로그인하고 나만의 8주 커리큘럼을 시작하세요</h1>
          <p>학습 유형 진단 결과에 맞춰 매일 할 일이 자동으로 배정돼요.</p>
        </div>
        <button
          type="button"
          className="primary-cta"
          onClick={onRequireAuth}
          style={{ margin: '0 auto', display: 'block' }}
        >
          로그인하기
        </button>
      </main>
    )
  }

  return (
    <main className="topik-page" id="top">
      <div className="topik-page-head">
        <button type="button" className="topik-back" onClick={onBack}>← TOPIK 메뉴로</button>
        <span className="topik-badge">TOPIK 코스</span>
        <h1>오늘의 커리큘럼</h1>
        <p>학습 유형 진단 결과에 맞춘 8주(56일) 커리큘럼을 하루 단위로 진행해요.</p>
      </div>

      <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
        {loading && <p>불러오는 중...</p>}

        {!loading && error === NEEDS_ASSESSMENT_MESSAGE && (
          <div>
            <p style={{ color: '#666' }}>{error}</p>
            <button
              type="button"
              onClick={onGoToAssessment}
              style={{
                padding: '10px 18px', cursor: 'pointer',
                backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '8px',
              }}
            >
              학습 유형 진단 하러 가기
            </button>
          </div>
        )}

        {!loading && error && error !== NEEDS_ASSESSMENT_MESSAGE && (
          <p style={{ color: '#dc3545' }}>⚠ {error}</p>
        )}

        {!loading && !error && data && (
          <>
            <div style={{
              display: 'flex', alignItems: 'center', gap: '14px', padding: '14px 18px', borderRadius: '12px',
              backgroundColor: ACCENT_TINT, marginBottom: '20px', flexWrap: 'wrap',
            }}>
              <span style={{ fontWeight: 700, fontSize: '16px', color: ACCENT_DARK }}>
                {data.curriculumTitle} · {data.learnerTypeLabel}
              </span>
              <span style={{ fontSize: '13px', color: '#666' }}>
                {data.completedDayCount} / {data.totalDays}일 완료
              </span>
            </div>

            <div style={{ height: '8px', backgroundColor: '#f3f4f6', borderRadius: '4px', marginBottom: '20px', overflow: 'hidden' }}>
              <div style={{
                height: '100%', width: `${Math.min(data.completedDayCount / data.totalDays, 1) * 100}%`,
                backgroundColor: ACCENT, transition: 'width 0.3s',
              }} />
            </div>

            {data.finished ? (
              <div style={{ textAlign: 'center', padding: '20px' }}>
                <div style={{ fontSize: '40px', marginBottom: '10px' }}>🎉</div>
                <p style={{ fontWeight: 700 }}>{data.task}</p>
              </div>
            ) : (
              <>
                <div style={{ fontSize: '12px', color: '#999', marginBottom: '4px' }}>
                  {data.weekNumber}주차 · {data.dayInWeek}일째 (전체 {data.dayNumber}일째) · {data.weekTitle}
                </div>
                <p style={{ fontSize: '13px', color: '#666', marginBottom: '16px' }}>{data.weekGoal}</p>

                <div style={{
                  fontSize: '18px', fontWeight: 700, padding: '18px', borderRadius: '12px',
                  backgroundColor: '#fff7ed', border: '1px solid ' + ACCENT_TINT, marginBottom: '16px',
                }}>
                  <ClickableKorean text={data.task} />
                </div>

                {data.template && (
                  <details style={{ marginBottom: '16px', fontSize: '13px', color: '#666' }}>
                    <summary style={{ cursor: 'pointer' }}>📎 이번 주 학습지 템플릿 보기</summary>
                    <pre style={{
                      marginTop: '8px', padding: '14px', backgroundColor: '#f9f9f9', borderRadius: '8px',
                      whiteSpace: 'pre-wrap', fontSize: '13px', lineHeight: 1.6, fontFamily: 'inherit',
                    }}>
                      {data.template}
                    </pre>
                  </details>
                )}

                {data.recommendedWords?.length > 0 && (
                  <div style={{ marginBottom: '20px' }}>
                    <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '6px' }}>
                      이번 주 추천 어휘 (눌러서 뜻 보기)
                    </label>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                      {data.recommendedWords.map((word, idx) => (
                        <span key={idx} style={{ padding: '4px 10px', backgroundColor: '#fff', borderRadius: '999px', border: '1px solid #ddd' }}>
                          <ClickableKorean text={word.text} /> = {word.meaning}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                <button
                  type="button"
                  onClick={handleComplete}
                  disabled={completing}
                  style={{
                    width: '100%', padding: '14px', fontSize: '16px', cursor: completing ? 'not-allowed' : 'pointer',
                    backgroundColor: completing ? '#ccc' : ACCENT, color: 'white', border: 'none', borderRadius: '8px',
                  }}
                >
                  {completing ? '처리 중...' : '오늘 학습 완료 →'}
                </button>
              </>
            )}
          </>
        )}
      </div>
    </main>
  )
}

export default TodayCurriculum
