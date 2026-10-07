import { useEffect, useState } from 'react'
import axios from 'axios'
import { API_BASE, authHeaders, hasToken } from '../api'
import { TAB_COLORS } from '../theme'
import ClickableKorean from './ClickableKorean'
import CurriculumPassageCard from './CurriculumPassageCard'
import { WrongNotesPanel } from './WrongNoteList'
import { Bi } from '../EnglishHint'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`

const ACCENT = TAB_COLORS.navigation.accent
const ACCENT_DARK = TAB_COLORS.navigation.dark
const ACCENT_TINT = TAB_COLORS.navigation.tint

const NEEDS_ASSESSMENT_MESSAGE = '먼저 학습 유형 진단을 완료해주세요.'

const LEARNER_TYPES = [
  { value: 'STRATEGIC_ANALYST', label: '전략적 분석가' },
  { value: 'VISUAL_IMMERSIVE', label: '시각적 몰입형' },
  { value: 'AUDITORY_EMPATHETIC', label: '청각적 교감형' },
  { value: 'EXPERIENTIAL_ACTOR', label: '체험적 실행형' },
  { value: 'ADAPTIVE_MIXED', label: '혼합 적응형' },
  { value: 'SNS_DEPENDENT', label: 'SNS 의존형' },
]

function TodayCurriculum({ onBack, onRequireAuth, onGoToAssessment }) {
  const loggedIn = hasToken()

  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [completing, setCompleting] = useState(false)
  const [assigning, setAssigning] = useState(false)

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

  const handleQuickAssign = async (learnerType) => {
    setAssigning(true)
    setError('')
    try {
      await axios.post(
        `${API_BASE}/api/assessment/quick-assign`,
        { learnerType },
        { headers: authHeaders({ 'Content-Type': 'application/json; charset=utf-8' }) },
      )
      await load()
    } catch (err) {
      setError(err.response?.data?.message || '유형 배정에 실패했어요.')
    } finally {
      setAssigning(false)
    }
  }

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
          <button type="button" className="topik-back" onClick={onBack}><Bi en="Back to TOPIK menu">← TOPIK 메뉴로</Bi></button>
          <span className="topik-badge">TOPIK 코스</span>
          <h1>
            로그인하고 나만의 8주 커리큘럼을 시작하세요
            <Bi en="Log in to start your own 8-week plan">{''}</Bi>
          </h1>
          <p><Bi en="Your daily tasks are assigned automatically from your learner-type result.">학습 유형 진단 결과에 맞춰 매일 할 일이 자동으로 배정돼요.</Bi></p>
        </div>
        <button
          type="button"
          className="primary-cta"
          onClick={onRequireAuth}
          style={{ margin: '0 auto', display: 'block' }}
        >
          <Bi en="Log in">로그인하기</Bi>
        </button>
      </main>
    )
  }

  return (
    <main className="topik-page" id="top">
      <div className="topik-page-head">
        <button type="button" className="topik-back" onClick={onBack}><Bi en="Back to TOPIK menu">← TOPIK 메뉴로</Bi></button>
        <span className="topik-badge">TOPIK 코스</span>
        <h1><Bi en="Today's curriculum">오늘의 커리큘럼</Bi></h1>
        <p><Bi en="Follow the 8-week plan matched to your learner-type result, one day at a time.">학습 유형 진단 결과에 맞춘 8주 커리큘럼을 하루 단위로 진행해요.</Bi></p>
      </div>

      <div style={{
        padding: '10px 14px', borderRadius: '8px', backgroundColor: '#f9fafb',
        border: '1px dashed #ddd', marginBottom: '16px', fontSize: '12px', color: '#888',
      }}>
        🔧 테스트용: 20문항 진단 없이 유형 바로 배정
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '6px' }}>
          {LEARNER_TYPES.map((type) => (
            <button
              key={type.value}
              type="button"
              onClick={() => handleQuickAssign(type.value)}
              disabled={assigning}
              style={{
                padding: '4px 10px', fontSize: '12px', borderRadius: '999px',
                border: '1px solid #ccc', backgroundColor: '#fff',
                cursor: assigning ? 'not-allowed' : 'pointer', color: '#555',
              }}
            >
              {type.label}
            </button>
          ))}
        </div>
      </div>

      <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
        {loading && <p><Bi en="Loading...">불러오는 중...</Bi></p>}

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
              <Bi en="Go to the learner-type test">학습 유형 진단 하러 가기</Bi>
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
                <Bi en={`${data.completedDayCount} / ${data.totalDays} days done`}>{data.completedDayCount} / {data.totalDays}일 완료</Bi>
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
                    <summary style={{ cursor: 'pointer' }}><Bi en="View this week's worksheet template">📎 이번 주 학습지 템플릿 보기</Bi></summary>
                    <pre style={{
                      marginTop: '8px', padding: '14px', backgroundColor: '#f9f9f9', borderRadius: '8px',
                      whiteSpace: 'pre-wrap', fontSize: '13px', lineHeight: 1.6, fontFamily: 'inherit',
                    }}>
                      {data.template}
                    </pre>
                  </details>
                )}

                {data.passages?.length > 0 && (
                  <div style={{ marginBottom: '20px' }}>
                    {data.passages.map((passage, idx) => (
                      <CurriculumPassageCard key={passage.id} passage={passage} index={idx} />
                    ))}
                  </div>
                )}

                {(!data.passages || data.passages.length === 0) && (
                  <div style={{ marginBottom: '20px' }}>
                    <div style={{
                      padding: '14px 16px', borderRadius: '10px', marginBottom: '14px',
                      backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', fontSize: '14px', lineHeight: 1.6,
                    }}>
                      <Bi en="Today is a review day. Revisit the questions you missed below, clear each one once you understand it, then press complete to move on.">
                        📝 오늘은 복습일이에요. 지금까지 풀다 틀린 문제를 아래에서 다시 확인하고,
                        이해했으면 각 문제의 삭제 버튼으로 정리하세요. 다 끝났으면 완료 버튼을 눌러 다음으로 넘어가요.
                      </Bi>
                    </div>
                    <WrongNotesPanel />
                  </div>
                )}

                {data.recommendedWords?.length > 0 && (
                  <div style={{ marginBottom: '20px' }}>
                    <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '6px' }}>
                      <Bi en="Recommended words this week (tap for meaning)">이번 주 추천 어휘 (눌러서 뜻 보기)</Bi>
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
                  <Bi en={completing ? 'Processing...' : 'Complete today →'}>{completing ? '처리 중...' : '오늘 학습 완료 →'}</Bi>
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
