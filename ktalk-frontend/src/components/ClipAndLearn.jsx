import { useState } from 'react'
import axios from 'axios'
import { AI_URL, LEARNING_URL } from '../api'

function ClipAndLearn() {
  const [query, setQuery] = useState('')
  const [videos, setVideos] = useState([])
  const [searchError, setSearchError] = useState('')
  const [isSearching, setIsSearching] = useState(false)

  const [selectedVideo, setSelectedVideo] = useState(null)
  const [progressId, setProgressId] = useState(null)
  const [completed, setCompleted] = useState(false)

  const [transcript, setTranscript] = useState('')
  const [quizCount, setQuizCount] = useState(3)
  const [quizzes, setQuizzes] = useState([])
  const [answers, setAnswers] = useState({})
  const [quizError, setQuizError] = useState('')
  const [isGeneratingQuiz, setIsGeneratingQuiz] = useState(false)

  const handleSearch = async (e) => {
    e.preventDefault()
    if (!query.trim()) return

    setIsSearching(true)
    setSearchError('')
    try {
      const response = await axios.get(`${AI_URL}/videos/search`, {
        params: { query, maxResults: 6 }
      })
      setVideos(response.data.data || [])
    } catch (error) {
      setSearchError(
          error.response?.data?.message ||
          '영상 검색에 실패했습니다. YouTube API 키가 설정되어 있는지 확인해주세요.'
      )
      setVideos([])
    } finally {
      setIsSearching(false)
    }
  }

  const handleSelectVideo = async (video) => {
    setSelectedVideo(video)
    setQuizzes([])
    setAnswers({})
    setCompleted(false)
    setProgressId(null)

    try {
      const response = await axios.post(`${LEARNING_URL}/start`, {
        videoId: video.videoId,
        videoTitle: video.title,
        thumbnailUrl: video.thumbnailUrl,
        channelName: video.channelName
      }, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' }
      })
      if (response.data.success) {
        setProgressId(response.data.data.id)
      }
    } catch (error) {
      console.error('학습 시작 실패:', error)
    }
  }

  const handleGenerateQuiz = async () => {
    if (!transcript.trim()) {
      alert('스크립트(대본)를 붙여넣어 주세요.')
      return
    }
    setIsGeneratingQuiz(true)
    setQuizError('')
    try {
      const response = await axios.post(`${AI_URL}/quiz/generate`, null, {
        params: { transcript, count: quizCount }
      })
      setQuizzes(response.data.data || [])
      setAnswers({})
    } catch (error) {
      setQuizError(error.response?.data?.message || '퀴즈 생성에 실패했습니다.')
    } finally {
      setIsGeneratingQuiz(false)
    }
  }

  const handleAnswer = (quizIndex, optionIndex) => {
    setAnswers((prev) => ({ ...prev, [quizIndex]: optionIndex }))
  }

  const handleComplete = async () => {
    if (!progressId) return
    try {
      await axios.post(`${LEARNING_URL}/${progressId}/complete`)
      setCompleted(true)
    } catch (error) {
      console.error('학습 완료 처리 실패:', error)
    }
  }

  return (
      <div>
        <div style={{ marginBottom: '20px', padding: '20px', border: '2px solid #6f42c1', borderRadius: '8px', backgroundColor: '#f8f5ff' }}>
          <h2>🎬 Clip & Learn</h2>
          <p>K-드라마/K-POP 영상을 검색하고, 대본을 붙여넣어 퀴즈로 학습해보세요.</p>
          <form onSubmit={handleSearch} style={{ display: 'flex', gap: '10px', marginTop: '15px' }}>
            <input
                type="text"
                placeholder="예: 사랑의 불시착, BTS 인터뷰"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                style={{ flex: 1, padding: '10px', fontSize: '16px' }}
            />
            <button type="submit" disabled={isSearching}
                    style={{ padding: '10px 20px', cursor: 'pointer', backgroundColor: '#6f42c1', color: 'white', border: 'none', borderRadius: '4px' }}>
              {isSearching ? '검색 중...' : '검색'}
            </button>
          </form>
          {searchError && <p style={{ color: '#dc3545', marginTop: '10px' }}>⚠ {searchError}</p>}
        </div>

        {videos.length > 0 && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '15px', marginBottom: '20px' }}>
              {videos.map((video) => (
                  <div key={video.videoId}
                       onClick={() => handleSelectVideo(video)}
                       style={{
                         border: selectedVideo?.videoId === video.videoId ? '2px solid #6f42c1' : '1px solid #ddd',
                         borderRadius: '8px', overflow: 'hidden', cursor: 'pointer'
                       }}>
                    {video.thumbnailUrl && <img src={video.thumbnailUrl} alt={video.title} style={{ width: '100%', display: 'block' }} />}
                    <div style={{ padding: '10px' }}>
                      <div style={{ fontWeight: 'bold', fontSize: '14px' }}>{video.title}</div>
                      <div style={{ fontSize: '12px', color: '#666' }}>{video.channelName}</div>
                    </div>
                  </div>
              ))}
            </div>
        )}

        {selectedVideo && (
            <div style={{ padding: '20px', border: '1px solid #ddd', borderRadius: '8px', marginBottom: '20px' }}>
              <h3>{selectedVideo.title}</h3>
              <div style={{ position: 'relative', paddingBottom: '56.25%', height: 0, margin: '15px 0' }}>
                <iframe
                    src={`https://www.youtube.com/embed/${selectedVideo.videoId}`}
                    title={selectedVideo.title}
                    style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', border: 0 }}
                    allowFullScreen
                />
              </div>

              <div style={{ marginTop: '15px' }}>
                <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>
                  대본/자막 붙여넣기 (퀴즈 생성용)
                </label>
                <textarea
                    value={transcript}
                    onChange={(e) => setTranscript(e.target.value)}
                    placeholder="영상 속 대사를 붙여넣으면 AI가 이해도 확인 퀴즈를 만들어줍니다."
                    style={{ width: '100%', minHeight: '100px', padding: '10px', fontSize: '14px' }}
                />
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center', marginTop: '10px' }}>
                  <label>문항 수:</label>
                  <select value={quizCount} onChange={(e) => setQuizCount(Number(e.target.value))} style={{ padding: '6px' }}>
                    <option value={3}>3</option>
                    <option value={5}>5</option>
                  </select>
                  <button onClick={handleGenerateQuiz} disabled={isGeneratingQuiz}
                          style={{ padding: '8px 16px', cursor: 'pointer', backgroundColor: '#6f42c1', color: 'white', border: 'none', borderRadius: '4px' }}>
                    {isGeneratingQuiz ? '퀴즈 생성 중...' : '퀴즈 생성'}
                  </button>
                  {progressId && !completed && (
                      <button onClick={handleComplete}
                              style={{ padding: '8px 16px', cursor: 'pointer', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: '4px' }}>
                        ✅ 학습 완료로 표시
                      </button>
                  )}
                  {completed && <span style={{ color: '#28a745', fontWeight: 'bold' }}>학습 완료! 🎉</span>}
                </div>
                {quizError && <p style={{ color: '#dc3545' }}>⚠ {quizError}</p>}
              </div>

              {quizzes.length > 0 && (
                  <div style={{ marginTop: '20px' }}>
                    <h4>퀴즈</h4>
                    {quizzes.map((quiz, qIdx) => {
                      const selected = answers[qIdx]
                      const answered = selected !== undefined
                      return (
                          <div key={qIdx} style={{ padding: '15px', marginBottom: '10px', border: '1px solid #eee', borderRadius: '8px' }}>
                            <p style={{ fontWeight: 'bold' }}>{qIdx + 1}. {quiz.question}</p>
                            {quiz.options.map((option, oIdx) => {
                              let bg = '#fff'
                              if (answered) {
                                if (oIdx === quiz.correctAnswerIndex) bg = '#d4edda'
                                else if (oIdx === selected) bg = '#f8d7da'
                              }
                              return (
                                  <div key={oIdx}
                                       onClick={() => !answered && handleAnswer(qIdx, oIdx)}
                                       style={{
                                         padding: '8px 12px', margin: '5px 0', borderRadius: '4px',
                                         border: '1px solid #ddd', backgroundColor: bg,
                                         cursor: answered ? 'default' : 'pointer'
                                       }}>
                                    {option}
                                  </div>
                              )
                            })}
                          </div>
                      )
                    })}
                  </div>
              )}
            </div>
        )}
      </div>
  )
}

export default ClipAndLearn
