// 설치(홈 화면 추가)를 위한 최소 서비스 워커. 오래된 빌드가 캐시에 남아 배포 후에도
// 옛 화면이 뜨는 문제를 피하려고 일부러 아무것도 캐시하지 않고 항상 네트워크로 통과시킨다.
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))
self.addEventListener('fetch', () => {})
