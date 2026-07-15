import { useEffect, useState } from 'react'
import axios from 'axios'
import { ASSESSMENT_URL } from '../api'
import { TAB_COLORS } from '../theme'

const ACCENT = TAB_COLORS.assessment.accent
const ACCENT_TINT = TAB_COLORS.assessment.tint

const AREA_LABELS = {
  A: '생활습관 & 집중력 패턴',
  B: '정보처리 선호도',
  C: '학습 동기 & 자기효능감',
  D: '디지털 도구 활용 능력',
  E: '학습 전략 & 메타인지',
}

const SCALE_LABELS = ['전혀 아니다', '아니다', '보통이다', '그렇다', '매우 그렇다']

function authHeaders() {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function AssessmentSurvey() {
  const [questions, setQuestions] = useState([])
  const [answers, setAnswers] = useState({})
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      try {
        const questionsRes = await axios.get(`${ASSESSMENT_URL}/questions`)
        setQuestions(questionsRes.data.data || [])

        if (localStorage.getItem('token')) {
          const resultRes = await axios
            .get(`${ASSESSMENT_URL}/result`, { headers: authHeaders() })
            .catch(() => null)
          if (resultRes?.data?.success) {
            setResult(resultRes.data.data)
          }
        }
      } catch (err) {
        setError('설문 문항을 불러오지 못했습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const handleAnswer = (code, score) => {
    setAnswers((prev) => ({ ...prev, [code]: score }))
  }

  const handleSubmit = async () => {
    if (!localStorage.getItem('token')) {
      alert('로그인 후 이용할 수 있습니다.')
      return
    }
    if (Object.keys(answers).length < questions.length) {
      alert('모든 문항에 답해주세요.')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      const payload = { answers: Object.entries(answers).map(([code, score]) => ({ code, score })) }
      const response = await axios.post(`${ASSESSMENT_URL}/submit`, payload, { headers: authHeaders() })
      if (response.data.success) {
        setResult(response.data.data)
      } else {
        setError(response.data.message)
      }
    } catch (err) {
      setError('진단 제출에 실패했습니다: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmitting(false)
    }
  }

  const handleRetake = () => {
    setResult(null)
    setAnswers({})
  }

  if (loading) return <p>불러오는 중...</p>

  if (result) {
    return (
      <div>
        <div style={{ padding: '20px', border: '2px solid ' + ACCENT, borderRadius: '8px', backgroundColor: ACCENT_TINT, marginBottom: '20px' }}>
          <h2>🧠 나의 학습 유형: {result.label}</h2>
          <p>{result.description}</p>
        </div>

        <div style={{ marginBottom: '20px' }}>
          <h3>맞춤 학습법</h3>
          <p>{result.studyTip}</p>
        </div>

        <div style={{ display: 'flex', gap: '15px', marginBottom: '20px', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '200px', padding: '15px', border: '1px solid #ddd', borderRadius: '8px' }}>
            <h4>추천 교재</h4>
            <ul>{result.textbooks.map((item) => <li key={item}>{item}</li>)}</ul>
          </div>
          <div style={{ flex: 1, minWidth: '200px', padding: '15px', border: '1px solid #ddd', borderRadius: '8px' }}>
            <h4>추천 강의</h4>
            <ul>{result.courses.map((item) => <li key={item}>{item}</li>)}</ul>
          </div>
          <div style={{ flex: 1, minWidth: '200px', padding: '15px', border: '1px solid #ddd', borderRadius: '8px' }}>
            <h4>추천 앱</h4>
            <ul>{result.apps.map((item) => <li key={item}>{item}</li>)}</ul>
          </div>
        </div>

        <div style={{ marginBottom: '20px' }}>
          <h3>추천 커리큘럼</h3>
          {result.curriculum.map((stage) => (
            <div key={stage.period} style={{ padding: '12px', marginBottom: '8px', border: '1px solid #eee', borderRadius: '8px' }}>
              <strong style={{ color: ACCENT }}>{stage.period}</strong>
              <p style={{ margin: '4px 0 0' }}>{stage.content}</p>
            </div>
          ))}
        </div>

        <button
          onClick={handleRetake}
          style={{ padding: '10px 16px', cursor: 'pointer', backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '4px' }}
        >
          다시 진단하기
        </button>
      </div>
    )
  }

  const groupedByArea = questions.reduce((acc, q) => {
    acc[q.area] = acc[q.area] || []
    acc[q.area].push(q)
    return acc
  }, {})

  return (
    <div>
      <div style={{ padding: '20px', border: '2px solid ' + ACCENT, borderRadius: '8px', backgroundColor: ACCENT_TINT, marginBottom: '20px' }}>
        <h2>🧠 학습 유형 진단</h2>
        <p>생활습관, 집중력 패턴, 학습 동기를 바탕으로 나에게 맞는 학습 전략을 찾아보세요. (약 5~7분 소요)</p>
      </div>

      {error && <p style={{ color: '#dc3545' }}>⚠ {error}</p>}

      {Object.entries(groupedByArea).map(([area, areaQuestions]) => (
        <div key={area} style={{ marginBottom: '20px' }}>
          <h3>{AREA_LABELS[area]}</h3>
          {areaQuestions.map((q) => (
            <div key={q.code} style={{ padding: '12px', marginBottom: '8px', border: '1px solid #eee', borderRadius: '8px' }}>
              <div style={{ marginBottom: '8px' }}>{q.text}</div>
              <div style={{ display: 'flex', gap: '8px' }}>
                {SCALE_LABELS.map((label, index) => {
                  const score = index + 1
                  const selected = answers[q.code] === score
                  return (
                    <button
                      key={score}
                      type="button"
                      onClick={() => handleAnswer(q.code, score)}
                      style={{
                        flex: 1,
                        padding: '8px 4px',
                        fontSize: '12px',
                        cursor: 'pointer',
                        border: '1px solid ' + (selected ? ACCENT : '#ddd'),
                        backgroundColor: selected ? ACCENT : 'white',
                        color: selected ? 'white' : '#333',
                        borderRadius: '4px',
                      }}
                    >
                      {label}
                    </button>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      ))}

      <button
        onClick={handleSubmit}
        disabled={submitting}
        style={{ padding: '12px 20px', cursor: 'pointer', backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '4px', fontSize: '16px' }}
      >
        {submitting ? '분석 중...' : '결과 확인하기'}
      </button>
    </div>
  )
}

export default AssessmentSurvey
