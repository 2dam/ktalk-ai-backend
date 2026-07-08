import { useEffect, useState } from 'react'
import axios from 'axios'
import { API_URL, LEARNING_URL } from '../api'
import { TAB_COLORS } from '../theme'

const ACCENT = TAB_COLORS.personalized.accent
const ACCENT_TINT = TAB_COLORS.personalized.tint

function PersonalizedLearning({ onNavigate }) {
  const [stats, setStats] = useState(null)
  const [active, setActive] = useState([])
  const [recommended, setRecommended] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadAll = async () => {
    setLoading(true)
    setError('')
    try {
      const [statsRes, activeRes, recommendRes] = await Promise.all([
        axios.get(`${LEARNING_URL}/stats`),
        axios.get(`${LEARNING_URL}/active`),
        axios.get(`${API_URL}/recommend`)
      ])
      setStats(statsRes.data.data)
      setActive(activeRes.data.data || [])
      setRecommended(recommendRes.data || [])
    } catch (err) {
      setError('학습 데이터를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const handleComplete = async (progressId) => {
    try {
      await axios.post(`${LEARNING_URL}/${progressId}/complete`)
      loadAll()
    } catch (err) {
      console.error('학습 완료 처리 실패:', err)
    }
  }

  if (loading) return <p>불러오는 중...</p>

  return (
      <div>
        <div style={{ padding: '20px', border: '2px solid ' + ACCENT, borderRadius: '8px', backgroundColor: ACCENT_TINT, marginBottom: '20px' }}>
          <h2>🎯 Personalized Learning</h2>
          <p>내 학습 현황을 확인하고, 아직 연습하지 않은 콘텐츠를 추천받아 보세요.</p>
        </div>

        {error && <p style={{ color: '#dc3545' }}>⚠ {error}</p>}

        <div style={{ display: 'flex', gap: '15px', marginBottom: '20px' }}>
          <div style={{ flex: 1, padding: '20px', border: '1px solid #ddd', borderRadius: '8px', textAlign: 'center' }}>
            <div style={{ fontSize: '32px', fontWeight: 'bold', color: ACCENT }}>{stats?.completedCount ?? 0}</div>
            <div style={{ color: '#666' }}>완료한 학습</div>
          </div>
          <div style={{ flex: 1, padding: '20px', border: '1px solid #ddd', borderRadius: '8px', textAlign: 'center' }}>
            <div style={{ fontSize: '32px', fontWeight: 'bold', color: ACCENT }}>{active.length}</div>
            <div style={{ color: '#666' }}>진행 중인 학습</div>
          </div>
        </div>

        <div style={{ marginBottom: '20px' }}>
          <h3>진행 중인 학습</h3>
          {active.length === 0 ? (
              <p style={{ color: '#999' }}>
                진행 중인 학습이 없습니다. Clip & Learn 탭에서 영상을 선택하면 여기에 표시됩니다.
              </p>
          ) : (
              active.map((progress) => (
                  <div key={progress.id} style={{
                    display: 'flex', alignItems: 'center', gap: '15px',
                    padding: '12px', marginBottom: '10px', border: '1px solid #eee', borderRadius: '8px'
                  }}>
                    {progress.thumbnailUrl && (
                        <img src={progress.thumbnailUrl} alt={progress.videoTitle} style={{ width: '100px', borderRadius: '4px' }} />
                    )}
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 'bold' }}>{progress.videoTitle}</div>
                      <div style={{ fontSize: '13px', color: '#666' }}>{progress.channelName}</div>
                      <div style={{ fontSize: '13px', color: ACCENT }}>진행률: {progress.progressPercent ?? 0}%</div>
                    </div>
                    <button onClick={() => handleComplete(progress.id)}
                            style={{ padding: '8px 14px', cursor: 'pointer', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: '4px' }}>
                      완료로 표시
                    </button>
                  </div>
              ))
          )}
        </div>

        <div>
          <h3>추천 콘텐츠</h3>
          {recommended.length === 0 ? (
              <p style={{ color: '#999' }}>추천할 콘텐츠가 없습니다. 콘텐츠 관리 탭에서 콘텐츠를 만들어보세요.</p>
          ) : (
              recommended.map((content) => (
                  <div key={content.id} style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    padding: '15px', marginBottom: '10px', border: '1px solid #eee', borderRadius: '8px'
                  }}>
                    <div>
                      <div style={{ fontWeight: 'bold' }}>{content.title}</div>
                      <div style={{ fontSize: '13px', color: '#666' }}>{content.description}</div>
                      <div style={{ fontSize: '12px', color: '#999', marginTop: '4px' }}>
                        카테고리: {content.category} | 레벨: {content.koreanLevel}
                        {(!content.dialogue) && <span style={{ color: '#fd7e14' }}> · 대화문 없음</span>}
                      </div>
                    </div>
                    <button onClick={() => onNavigate?.('contents')}
                            style={{ padding: '8px 14px', cursor: 'pointer', backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '4px', whiteSpace: 'nowrap' }}>
                      학습하러 가기
                    </button>
                  </div>
              ))
          )}
        </div>
      </div>
  )
}

export default PersonalizedLearning
