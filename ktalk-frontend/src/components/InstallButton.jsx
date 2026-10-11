import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Bi } from '../EnglishHint'

const GUIDE_PDF = '/guides/KTalk_Install_Guide_EN.pdf'

const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true
const isAndroid = () => /android/i.test(window.navigator.userAgent)
// 카카오톡/네이버/인스타그램 등 앱 안에서 열린 화면(인앱 브라우저)에서는 홈 화면 추가가 막혀 있다.
const isInAppBrowser = () => /KAKAOTALK|NAVER\(inapp|Instagram|FBAN|FBAV|Line\/|DaumApps/i.test(window.navigator.userAgent)

// iOS 공유 버튼(네모 위에 화살표) 모양. 글로 설명하는 것보다 실제 아이콘을 보여주는 편이 찾기 쉽다.
function ShareIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 15V3" />
      <path d="M8 7l4-4 4 4" />
      <path d="M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1" />
    </svg>
  )
}

function DotsIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx="12" cy="5" r="2" />
      <circle cx="12" cy="12" r="2" />
      <circle cx="12" cy="19" r="2" />
    </svg>
  )
}

function MenuLinesIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  )
}

const IPHONE_STEPS = [
  {
    ko: 'Safari 앱에서 이 사이트를 열어 주세요. (카카오톡·네이버 등 앱 안에서 열었다면 Safari로 옮겨야 해요.)',
    en: 'Open this site in the Safari app. (If you opened it inside KakaoTalk or Naver, move it to Safari first.)',
    icon: '🧭',
  },
  {
    ko: '주소창 안(또는 화면 아래)에 있는 공유 버튼을 누르세요. 네모 위에 화살표(↑)가 있는 모양이에요.',
    en: 'Tap the Share button in the address bar (or at the bottom). It looks like a box with an arrow (↑).',
    icon: <ShareIcon />,
  },
  {
    ko: '메뉴를 아래로 밀어서 "홈 화면에 추가"를 누르세요. 안 보이면 맨 아래 "행동 편집"에서 추가할 수 있어요.',
    en: 'Scroll down and tap "Add to Home Screen". If you do not see it, add it from "Edit Actions" at the bottom.',
    icon: '➕',
  },
  {
    ko: '오른쪽 위의 "추가"를 누르면 끝! 홈 화면에 K-Talk 아이콘이 생겨요.',
    en: 'Tap "Add" at the top right. Done! The K-Talk icon appears on your home screen.',
    icon: '✅',
  },
]

const GALAXY_CHROME_STEPS = [
  {
    ko: 'Chrome 앱에서 이 사이트를 열어 주세요.',
    en: 'Open this site in the Chrome app.',
    icon: '🌐',
  },
  {
    ko: '오른쪽 위의 점 세 개(⋮) 메뉴를 누르세요.',
    en: 'Tap the three-dot menu (⋮) at the top right.',
    icon: <DotsIcon />,
  },
  {
    ko: '"홈 화면에 추가" 또는 "앱 설치"를 누르세요.',
    en: 'Tap "Add to Home screen" or "Install app".',
    icon: '➕',
  },
  {
    ko: '"설치" 또는 "추가"를 누르면 끝! 홈 화면에 K-Talk 아이콘이 생겨요.',
    en: 'Tap "Install" or "Add". Done! The K-Talk icon appears on your home screen.',
    icon: '✅',
  },
]

const GALAXY_SAMSUNG_STEPS = [
  {
    ko: '화면 오른쪽 아래의 메뉴(줄 세 개 ≡)를 누르세요.',
    en: 'Tap the menu (three lines ≡) at the bottom right.',
    icon: <MenuLinesIcon />,
  },
  {
    ko: '"현재 페이지 추가" → "홈 화면"을 고르고 "추가"를 누르세요.',
    en: 'Tap "Add page to" → "Home screen", then tap "Add".',
    icon: '➕',
  },
]

function StepList({ steps }) {
  return (
    <ol className="ios-install-steps">
      {steps.map((step, index) => (
        <li key={index}>
          <span className="ios-install-icon">{step.icon}</span>
          <span><b>{index + 1}.</b> <Bi en={step.en}>{step.ko}</Bi></span>
        </li>
      ))}
    </ol>
  )
}

function InstallGuide({ onClose, installEvent, onNativeInstall }) {
  const inApp = isInAppBrowser()
  const [tab, setTab] = useState(() => (isAndroid() ? 'galaxy' : 'iphone'))
  const [copied, setCopied] = useState(false)

  // 공유 메뉴에 "홈 화면에 추가"가 없는 화면(앱 안 브라우저 등)에서는 기본 브라우저로 옮겨야 하므로 주소를 복사해 준다.
  const copyLink = async () => {
    const url = window.location.origin + '/'
    try {
      await navigator.clipboard.writeText(url)
    } catch {
      const input = document.createElement('input')
      input.value = url
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
    }
    setCopied(true)
  }

  return createPortal(
    <div className="ios-install-overlay" onClick={onClose}>
      <div className="ios-install-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="ios-install-close" onClick={onClose} aria-label="닫기">×</button>
        <h2><Bi en="Install K-Talk on your phone">앱 설치 가이드</Bi></h2>

        <div className="install-tabs" role="tablist">
          <button type="button" role="tab" aria-selected={tab === 'iphone'} className={tab === 'iphone' ? 'on' : ''} onClick={() => setTab('iphone')}>
            iPhone
          </button>
          <button type="button" role="tab" aria-selected={tab === 'galaxy'} className={tab === 'galaxy' ? 'on' : ''} onClick={() => setTab('galaxy')}>
            Galaxy
          </button>
        </div>

        {inApp && (
          <p className="ios-install-warn">
            <Bi en="You are inside another app's browser. Open this page in Safari (iPhone) or Chrome (Galaxy) first, then follow the steps.">
              지금은 카카오톡/네이버 같은 앱 안에서 열린 화면이에요. 메뉴에서 &quot;다른 브라우저로 열기&quot;를 눌러 아이폰은 Safari, 갤럭시는 Chrome에서 먼저 열어 주세요.
            </Bi>
          </p>
        )}

        {tab === 'iphone' && <StepList steps={IPHONE_STEPS} />}

        {tab === 'galaxy' && (
          <>
            {installEvent && (
              <button type="button" className="install-native" onClick={onNativeInstall}>
                <Bi en="Install now">지금 바로 설치하기</Bi>
              </button>
            )}
            <h3 className="install-sub"><Bi en="Chrome (recommended)">Chrome (권장)</Bi></h3>
            <StepList steps={GALAXY_CHROME_STEPS} />
            <h3 className="install-sub"><Bi en="Samsung Internet">삼성 인터넷</Bi></h3>
            <StepList steps={GALAXY_SAMSUNG_STEPS} />
          </>
        )}

        <div className="ios-install-fallback">
          <b><Bi en="Can't find the Home Screen option?">홈 화면 추가 메뉴가 안 보이나요?</Bi></b>
          <p>
            <Bi en="You are probably in a browser inside another app. Copy the link, open Safari (iPhone) or Chrome (Galaxy), paste it into the address bar, then repeat the steps above.">
              다른 앱 안에서 열린 화면일 수 있어요. 아래 버튼으로 주소를 복사한 뒤, 아이폰은 <b>Safari</b>, 갤럭시는 <b>Chrome</b>을 열어 주소창에 붙여넣고 위 단계를 다시 해 주세요.
            </Bi>
          </p>
          <button type="button" className="ios-install-copy" onClick={copyLink}>
            <Bi en={copied ? 'Copied! Paste it in your browser.' : 'Copy link'}>{copied ? '복사됨! 브라우저에 붙여넣으세요' : '주소 복사'}</Bi>
          </button>
          <small className="ios-install-url">{window.location.origin}/</small>
        </div>

        <a className="install-pdf" href={GUIDE_PDF} target="_blank" rel="noopener noreferrer">
          📄 <Bi en="Open the PDF guide (English)">영문 PDF 설명서 보기</Bi>
        </a>

        <button type="button" className="ios-install-ok" onClick={onClose}><Bi en="Got it">알겠어요</Bi></button>
      </div>
    </div>,
    document.body,
  )
}

/**
 * "앱 설치"(홈 화면에 바로가기 추가) 버튼. 누르면 항상 아이폰/갤럭시 설치 가이드를 보여주고,
 * 크롬이 설치 프롬프트를 제공하는 환경(갤럭시/데스크톱)에서는 가이드 안에 "지금 바로 설치" 버튼을 함께 둔다.
 * 이미 설치된 앱(standalone)으로 열렸으면 아무것도 그리지 않는다.
 */
function InstallButton() {
  const [installEvent, setInstallEvent] = useState(null)
  const [showGuide, setShowGuide] = useState(false)
  const [installed, setInstalled] = useState(() => isStandalone())

  useEffect(() => {
    const onPrompt = (event) => {
      event.preventDefault()
      setInstallEvent(event)
    }
    const onInstalled = () => { setInstalled(true); setInstallEvent(null); setShowGuide(false) }
    window.addEventListener('beforeinstallprompt', onPrompt)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  if (installed) return null

  const nativeInstall = async () => {
    if (!installEvent) return
    installEvent.prompt()
    await installEvent.userChoice
    setInstallEvent(null)
  }

  return (
    <>
      <button type="button" className="login-button" onClick={() => setShowGuide(true)}>
        <Bi en="Install app">앱 설치</Bi>
      </button>
      {showGuide && (
        <InstallGuide
          onClose={() => setShowGuide(false)}
          installEvent={installEvent}
          onNativeInstall={nativeInstall}
        />
      )}
    </>
  )
}

export default InstallButton
