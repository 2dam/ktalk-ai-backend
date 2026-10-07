import { useEffect, useState } from 'react'
import axios from 'axios'
import { API_BASE, authHeaders, hasToken } from '../api'
import { TAB_COLORS } from '../theme'
import ClickableKorean from './ClickableKorean'
import CurriculumPassageCard from './CurriculumPassageCard'
import { WrongNotesPanel } from './WrongNoteList'
import { withEulReul } from '../korean'
import { Bi } from '../EnglishHint'
import PrintMenu from './PrintSheet'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`

const ACCENT = TAB_COLORS.navigation.accent
const ACCENT_TINT = TAB_COLORS.navigation.tint

const NEEDS_ASSESSMENT_MESSAGE = '먼저 학습 유형 진단을 완료해주세요.'

/**
 * 배정된 커리큘럼을 주차 단위로 훑어보는 화면. "오늘의 학습"과 달리 진행 순서와
 * 무관하게 아무 주/일이나 골라 볼 수 있다 — 기출문제집(filterWeeks로 일반 주차만)과
 * 모의고사(filterWeeks로 모의고사/Final 주차만) 화면이 이 컴포넌트를 함께 쓴다.
 */
function TopikWeekBrowser({ heading, headingEn, description, descriptionEn, filterWeeks, emptyMessage, onBack, onRequireAuth, onGoToAssessment }) {
  const loggedIn = hasToken()

  const [weeks, setWeeks] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [selectedWeek, setSelectedWeek] = useState(null)
  const [dayNumber, setDayNumber] = useState(null)
  const [dayContent, setDayContent] = useState(null)
  const [dayLoading, setDayLoading] = useState(false)
  const [dayError, setDayError] = useState('')

  useEffect(() => {
    if (!loggedIn) {
      setLoading(false)
      return
    }
    const load = async () => {
      setLoading(true)
      setError('')
      try {
        const res = await axios.get(`${CURRICULUM_URL}/weeks`, { headers: authHeaders() })
        if (res.data?.success) {
          setWeeks(res.data.data)
        } else {
          setError(res.data?.message || '주차 목록을 불러오지 못했어요.')
        }
      } catch (err) {
        setError(err.response?.data?.message || '주차 목록을 불러오지 못했어요.')
      } finally {
        setLoading(false)
      }
    }
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openDay = async (day) => {
    setDayNumber(day.dayNumber)
    setDayContent(null)
    setDayLoading(true)
    setDayError('')
    try {
      const res = await axios.get(`${CURRICULUM_URL}/days/${day.dayNumber}`, { headers: authHeaders() })
      if (res.data?.success) {
        setDayContent(res.data.data)
      } else {
        setDayError(res.data?.message || '학습 내용을 불러오지 못했어요.')
      }
    } catch (err) {
      setDayError(err.response?.data?.message || '학습 내용을 불러오지 못했어요.')
    } finally {
      setDayLoading(false)
    }
  }

  if (!loggedIn) {
    return (
      <main className="topik-page" id="top">
        <div className="topik-page-head">
          <button type="button" className="topik-back" onClick={onBack}><Bi en="Back to TOPIK menu">← TOPIK 메뉴로</Bi></button>
          <span className="topik-badge">TOPIK 코스</span>
          <h1>
            로그인하고 {withEulReul(heading)} 시작하세요
            <Bi en={`Log in to start ${headingEn}`}>{''}</Bi>
          </h1>
          <p><Bi en="Content is assigned automatically based on your learner-type result.">학습 유형 진단 결과에 맞춰 콘텐츠가 자동으로 배정돼요.</Bi></p>
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

  const visibleWeeks = weeks ? filterWeeks(weeks) : []

  const backTarget = () => {
    if (dayNumber !== null) {
      setDayNumber(null)
      setDayContent(null)
      setDayError('')
      return
    }
    if (selectedWeek !== null) {
      setSelectedWeek(null)
      return
    }
    onBack()
  }

  return (
    <main className="topik-page" id="top">
      <div className="topik-page-head">
        <button type="button" className="topik-back" onClick={backTarget}><Bi en="Back">← 이전으로</Bi></button>
        <span className="topik-badge">TOPIK 코스</span>
        <h1><Bi en={headingEn}>{heading}</Bi></h1>
        <p><Bi en={descriptionEn}>{description}</Bi></p>
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

        {!loading && !error && dayNumber !== null && (
          <>
            {dayLoading && <p><Bi en="Loading...">불러오는 중...</Bi></p>}
            {!dayLoading && dayError && <p style={{ color: '#dc3545' }}>⚠ {dayError}</p>}
            {!dayLoading && !dayError && dayContent && (
              <>
                <div style={{ fontSize: '12px', color: '#999', marginBottom: '4px' }}>
                  {dayContent.weekNumber}주차 · {dayContent.dayInWeek}일째 · {dayContent.weekTitle}
                </div>
                <p style={{ fontSize: '13px', color: '#666', marginBottom: '16px' }}>{dayContent.weekGoal}</p>

                <div style={{
                  fontSize: '18px', fontWeight: 700, padding: '18px', borderRadius: '12px',
                  backgroundColor: '#fff7ed', border: '1px solid ' + ACCENT_TINT, marginBottom: '16px',
                }}>
                  <ClickableKorean text={dayContent.task} />
                </div>

                <PrintMenu day={dayNumber} label="이 회차 인쇄 / PDF 저장" labelEn="Print this session / Save as PDF" />

                {dayContent.template && (
                  <details style={{ marginBottom: '16px', fontSize: '13px', color: '#666' }}>
                    <summary style={{ cursor: 'pointer' }}><Bi en="View this session's worksheet template">📎 이 회차 학습지 템플릿 보기</Bi></summary>
                    <pre style={{
                      marginTop: '8px', padding: '14px', backgroundColor: '#f9f9f9', borderRadius: '8px',
                      whiteSpace: 'pre-wrap', fontSize: '13px', lineHeight: 1.6, fontFamily: 'inherit',
                    }}>
                      {dayContent.template}
                    </pre>
                  </details>
                )}

                {(!dayContent.passages || dayContent.passages.length === 0) && (
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

                {dayContent.passages?.length > 0 && (
                  <div>
                    {dayContent.passages.map((passage, idx) => (
                      <CurriculumPassageCard key={passage.id} passage={passage} index={idx} />
                    ))}
                  </div>
                )}
              </>
            )}
          </>
        )}

        {!loading && !error && dayNumber === null && selectedWeek !== null && (
          <>
            <h2 style={{ marginTop: 0 }}>{selectedWeek.title}</h2>
            <PrintMenu week={selectedWeek.weekNumber} label="이 주차 전체 인쇄 / PDF 저장" labelEn="Print the whole week / Save as PDF" />
            <p style={{ fontSize: '13px', color: '#666', marginBottom: '16px' }}>{selectedWeek.goal}</p>
            <div className="topik-page-grid">
              {selectedWeek.days.map((day) => (
                <button
                  type="button"
                  className="topik-page-card"
                  key={day.dayNumber}
                  onClick={() => openDay(day)}
                >
                  <b><Bi en={`Session ${day.dayInWeek}`}>{day.dayInWeek}회차</Bi></b>
                  <small>{day.task}</small>
                </button>
              ))}
            </div>
          </>
        )}

        {!loading && !error && dayNumber === null && selectedWeek === null && (
          <>
            {visibleWeeks.length === 0 ? (
              <p style={{ color: '#666' }}>{emptyMessage}</p>
            ) : (
              <div className="topik-page-grid">
                {visibleWeeks.map((week) => (
                  <button
                    type="button"
                    className="topik-page-card"
                    key={week.weekNumber}
                    onClick={() => setSelectedWeek(week)}
                  >
                    <b>{week.title}</b>
                    <small>{week.goal}</small>
                  </button>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </main>
  )
}

export default TopikWeekBrowser
