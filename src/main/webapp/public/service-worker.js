/**
 * CardDemo Credit Card Management System - Service Worker
 * 
 * Progressive Web App service worker providing offline functionality for
 * the CardDemo React application. Implements intelligent caching strategies
 * for React components, API responses, and static assets to ensure continued
 * operation during network interruptions for credit card management operations.
 * 
 * Based on Spring Boot/React architecture with JWT authentication,
 * PostgreSQL database, and Redis session management.
 * 
 * @version 1.0.0
 * @author Blitzy Platform Engineering Team
 */

// Service Worker Version and Cache Names
const CACHE_VERSION = '1.0.0';
const STATIC_CACHE_NAME = `carddemo-static-v${CACHE_VERSION}`;
const DYNAMIC_CACHE_NAME = `carddemo-dynamic-v${CACHE_VERSION}`;
const API_CACHE_NAME = `carddemo-api-v${CACHE_VERSION}`;
const OFFLINE_CACHE_NAME = `carddemo-offline-v${CACHE_VERSION}`;

// Cache Configuration
const CACHE_EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
const MAX_CACHE_SIZE = 50; // Maximum number of cached items per cache
const BACKGROUND_SYNC_TAG = 'carddemo-background-sync';
const OFFLINE_FALLBACK_URL = '/offline.html';

// Static Assets to Cache (Cache-First Strategy)
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/offline.html',
  '/manifest.json',
  '/static/css/main.css',
  '/static/js/main.js',
  '/static/js/vendors.js',
  '/static/js/runtime.js',
  '/icons/icon-72x72.png',
  '/icons/icon-96x96.png',
  '/icons/icon-128x128.png',
  '/icons/icon-144x144.png',
  '/icons/icon-152x152.png',
  '/icons/icon-192x192.png',
  '/icons/icon-384x384.png',
  '/icons/icon-512x512.png',
  '/screenshots/desktop-main-menu.png',
  '/screenshots/mobile-account-view.png',
  '/screenshots/desktop-transaction-list.png',
  '/screenshots/mobile-card-list.png',
  '/icons/shortcut-account-192x192.png',
  '/icons/shortcut-card-192x192.png',
  '/icons/shortcut-transaction-192x192.png',
  '/icons/shortcut-payment-192x192.png'
];

// API Endpoints for Network-First Strategy
const API_ENDPOINTS = [
  '/api/v1/auth/',
  '/api/v1/accounts/',
  '/api/v1/cards/',
  '/api/v1/transactions/',
  '/api/v1/payments/',
  '/api/v1/users/',
  '/api/v1/reports/',
  '/api/v1/menu/'
];

// Offline-capable endpoints that can be queued for background sync
const SYNC_ENDPOINTS = [
  '/api/v1/transactions',
  '/api/v1/payments',
  '/api/v1/accounts',
  '/api/v1/cards'
];

/**
 * Service Worker Install Event Handler
 * 
 * Implements cache-first strategy for static assets including React components,
 * CSS, JavaScript bundles, and PWA icons. Pre-caches essential application
 * resources to ensure offline availability.
 * 
 * @param {ExtendableEvent} event - Service worker install event
 */
function install(event) {
  console.log('[ServiceWorker] Install event triggered');
  
  event.waitUntil(
    (async () => {
      try {
        // Open static assets cache
        const staticCache = await caches.open(STATIC_CACHE_NAME);
        
        // Pre-cache static assets with cache-first strategy
        console.log('[ServiceWorker] Pre-caching static assets');
        await staticCache.addAll(STATIC_ASSETS);
        
        // Create offline fallback page
        const offlineResponse = new Response(
          generateOfflinePage(),
          {
            headers: {
              'Content-Type': 'text/html',
              'Cache-Control': 'no-cache'
            }
          }
        );
        
        // Cache offline fallback page
        await staticCache.put(OFFLINE_FALLBACK_URL, offlineResponse);
        
        // Initialize other caches
        await caches.open(DYNAMIC_CACHE_NAME);
        await caches.open(API_CACHE_NAME);
        await caches.open(OFFLINE_CACHE_NAME);
        
        console.log('[ServiceWorker] Static assets cached successfully');
        
        // Skip waiting to activate immediately
        self.skipWaiting();
        
      } catch (error) {
        console.error('[ServiceWorker] Install failed:', error);
        throw error;
      }
    })()
  );
}

/**
 * Service Worker Activate Event Handler
 * 
 * Handles service worker activation, cache cleanup, and client claiming.
 * Removes outdated caches and ensures all clients are controlled by the
 * new service worker version.
 * 
 * @param {ExtendableEvent} event - Service worker activate event
 */
function activate(event) {
  console.log('[ServiceWorker] Activate event triggered');
  
  event.waitUntil(
    (async () => {
      try {
        // Get all cache names
        const cacheNames = await caches.keys();
        
        // Define current cache names
        const currentCaches = [
          STATIC_CACHE_NAME,
          DYNAMIC_CACHE_NAME,
          API_CACHE_NAME,
          OFFLINE_CACHE_NAME
        ];
        
        // Delete outdated caches
        const deletePromises = cacheNames
          .filter(cacheName => !currentCaches.includes(cacheName))
          .map(cacheName => {
            console.log('[ServiceWorker] Deleting outdated cache:', cacheName);
            return caches.delete(cacheName);
          });
        
        await Promise.all(deletePromises);
        
        // Clean up expired cache entries
        await cleanupExpiredCaches();
        
        // Claim all clients immediately
        await self.clients.claim();
        
        console.log('[ServiceWorker] Activation complete');
        
      } catch (error) {
        console.error('[ServiceWorker] Activation failed:', error);
        throw error;
      }
    })()
  );
}

/**
 * Service Worker Fetch Event Handler
 * 
 * Implements intelligent caching strategies based on request type:
 * - Cache-first for static assets (CSS, JS, images)
 * - Network-first for API calls with fallback to cache
 * - Offline fallback for navigation requests
 * 
 * Ensures continued operation during network interruptions for credit card
 * management operations while maintaining security for authenticated requests.
 * 
 * @param {FetchEvent} event - Service worker fetch event
 */
function fetch(event) {
  const { request } = event;
  const url = new URL(request.url);
  
  // Handle different request types with appropriate caching strategies
  if (isStaticAsset(request)) {
    // Cache-first strategy for static assets
    event.respondWith(handleStaticAssetRequest(request));
  } else if (isApiRequest(request)) {
    // Network-first strategy for API calls
    event.respondWith(handleApiRequest(request));
  } else if (isNavigationRequest(request)) {
    // Navigation requests with offline fallback
    event.respondWith(handleNavigationRequest(request));
  } else {
    // Default network-first strategy
    event.respondWith(handleDefaultRequest(request));
  }
}

/**
 * Service Worker Background Sync Event Handler
 * 
 * Handles background synchronization for offline data submission.
 * Queues failed requests for retry when network connectivity is restored,
 * ensuring data integrity for credit card transactions and account updates.
 * 
 * @param {SyncEvent} event - Background sync event
 */
function sync(event) {
  console.log('[ServiceWorker] Background sync triggered:', event.tag);
  
  if (event.tag === BACKGROUND_SYNC_TAG) {
    event.waitUntil(
      (async () => {
        try {
          await processPendingRequests();
          console.log('[ServiceWorker] Background sync completed successfully');
        } catch (error) {
          console.error('[ServiceWorker] Background sync failed:', error);
          throw error;
        }
      })()
    );
  }
}

/**
 * Service Worker Push Event Handler
 * 
 * Handles push notification events for real-time transaction alerts.
 * Processes incoming push messages and displays notifications for
 * critical events like transaction authorizations and account updates.
 * 
 * @param {PushEvent} event - Push notification event
 */
function push(event) {
  console.log('[ServiceWorker] Push notification received');
  
  event.waitUntil(
    (async () => {
      try {
        let notificationData = {};
        
        // Parse push message data
        if (event.data) {
          try {
            notificationData = event.data.json();
          } catch (error) {
            console.warn('[ServiceWorker] Invalid push data format, using defaults');
            notificationData = {
              title: 'CardDemo Notification',
              body: 'You have a new notification',
              type: 'info'
            };
          }
        }
        
        // Create notification options based on message type
        const notificationOptions = createNotificationOptions(notificationData);
        
        // Show notification
        await self.registration.showNotification(
          notificationData.title || 'CardDemo Notification',
          notificationOptions
        );
        
        console.log('[ServiceWorker] Push notification displayed');
        
      } catch (error) {
        console.error('[ServiceWorker] Push notification failed:', error);
        throw error;
      }
    })()
  );
}

/**
 * Service Worker Notification Click Event Handler
 * 
 * Handles user interactions with push notifications.
 * Opens appropriate application pages based on notification type
 * and maintains user context for transaction-related notifications.
 * 
 * @param {NotificationEvent} event - Notification click event
 */
function notificationclick(event) {
  console.log('[ServiceWorker] Notification clicked:', event.notification.data);
  
  event.notification.close();
  
  event.waitUntil(
    (async () => {
      try {
        const notificationData = event.notification.data || {};
        const targetUrl = determineTargetUrl(notificationData);
        
        // Get all clients
        const clients = await self.clients.matchAll({
          type: 'window',
          includeUncontrolled: true
        });
        
        // Find existing client or open new one
        let targetClient = null;
        
        for (const client of clients) {
          if (client.url === targetUrl && 'focus' in client) {
            targetClient = client;
            break;
          }
        }
        
        if (targetClient) {
          // Focus existing client
          await targetClient.focus();
          
          // Send message to client if needed
          if (notificationData.action) {
            targetClient.postMessage({
              type: 'notification-action',
              action: notificationData.action,
              data: notificationData
            });
          }
        } else {
          // Open new client
          await self.clients.openWindow(targetUrl);
        }
        
        console.log('[ServiceWorker] Notification click handled');
        
      } catch (error) {
        console.error('[ServiceWorker] Notification click handling failed:', error);
        throw error;
      }
    })()
  );
}

// Helper Functions

/**
 * Determines if a request is for a static asset
 * @param {Request} request - The fetch request
 * @returns {boolean} True if the request is for a static asset
 */
function isStaticAsset(request) {
  const url = new URL(request.url);
  return STATIC_ASSETS.some(asset => url.pathname === asset) ||
         url.pathname.startsWith('/static/') ||
         url.pathname.startsWith('/icons/') ||
         url.pathname.startsWith('/screenshots/') ||
         url.pathname.match(/\.(css|js|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot)$/);
}

/**
 * Determines if a request is for an API endpoint
 * @param {Request} request - The fetch request
 * @returns {boolean} True if the request is for an API endpoint
 */
function isApiRequest(request) {
  const url = new URL(request.url);
  return API_ENDPOINTS.some(endpoint => url.pathname.startsWith(endpoint));
}

/**
 * Determines if a request is a navigation request
 * @param {Request} request - The fetch request
 * @returns {boolean} True if the request is a navigation request
 */
function isNavigationRequest(request) {
  return request.mode === 'navigate' || 
         (request.method === 'GET' && request.headers.get('accept').includes('text/html'));
}

/**
 * Handles static asset requests with cache-first strategy
 * @param {Request} request - The fetch request
 * @returns {Promise<Response>} The cached or fetched response
 */
async function handleStaticAssetRequest(request) {
  try {
    // Try cache first
    const cachedResponse = await caches.match(request, { cacheName: STATIC_CACHE_NAME });
    
    if (cachedResponse) {
      console.log('[ServiceWorker] Serving static asset from cache:', request.url);
      return cachedResponse;
    }
    
    // Fetch from network and cache
    const networkResponse = await fetch(request);
    
    if (networkResponse.ok) {
      const cache = await caches.open(STATIC_CACHE_NAME);
      await cache.put(request, networkResponse.clone());
      console.log('[ServiceWorker] Static asset cached from network:', request.url);
    }
    
    return networkResponse;
    
  } catch (error) {
    console.error('[ServiceWorker] Static asset request failed:', error);
    
    // Return cached version if available
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }
    
    // Return offline fallback for critical assets
    return new Response('Asset not available offline', { status: 503 });
  }
}

/**
 * Handles API requests with network-first strategy and caching
 * @param {Request} request - The fetch request
 * @returns {Promise<Response>} The network or cached response
 */
async function handleApiRequest(request) {
  try {
    // Try network first
    const networkResponse = await fetch(request);
    
    if (networkResponse.ok) {
      // Cache successful GET requests
      if (request.method === 'GET') {
        const cache = await caches.open(API_CACHE_NAME);
        await cache.put(request, networkResponse.clone());
        console.log('[ServiceWorker] API response cached:', request.url);
      }
      
      return networkResponse;
    }
    
    // If network fails, try cache for GET requests
    if (request.method === 'GET') {
      const cachedResponse = await caches.match(request, { cacheName: API_CACHE_NAME });
      if (cachedResponse) {
        console.log('[ServiceWorker] Serving API response from cache:', request.url);
        return cachedResponse;
      }
    }
    
    return networkResponse;
    
  } catch (error) {
    console.error('[ServiceWorker] API request failed:', error);
    
    // For GET requests, try cache
    if (request.method === 'GET') {
      const cachedResponse = await caches.match(request, { cacheName: API_CACHE_NAME });
      if (cachedResponse) {
        console.log('[ServiceWorker] Serving API response from cache after network failure:', request.url);
        return cachedResponse;
      }
    }
    
    // Queue non-GET requests for background sync if applicable
    if (request.method !== 'GET' && isSyncableRequest(request)) {
      await queueRequestForSync(request);
      return new Response(
        JSON.stringify({ 
          message: 'Request queued for background sync',
          queued: true 
        }),
        {
          status: 202,
          headers: { 'Content-Type': 'application/json' }
        }
      );
    }
    
    return new Response('Service temporarily unavailable', { status: 503 });
  }
}

/**
 * Handles navigation requests with offline fallback
 * @param {Request} request - The fetch request
 * @returns {Promise<Response>} The navigation response
 */
async function handleNavigationRequest(request) {
  try {
    // Try network first
    const networkResponse = await fetch(request);
    
    if (networkResponse.ok) {
      // Cache successful navigation responses
      const cache = await caches.open(DYNAMIC_CACHE_NAME);
      await cache.put(request, networkResponse.clone());
      return networkResponse;
    }
    
    // Fallback to cached version
    const cachedResponse = await caches.match(request, { cacheName: DYNAMIC_CACHE_NAME });
    if (cachedResponse) {
      return cachedResponse;
    }
    
    // Return offline fallback page
    return caches.match(OFFLINE_FALLBACK_URL);
    
  } catch (error) {
    console.error('[ServiceWorker] Navigation request failed:', error);
    
    // Try cached version
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }
    
    // Return offline fallback page
    return caches.match(OFFLINE_FALLBACK_URL);
  }
}

/**
 * Handles default requests with basic caching
 * @param {Request} request - The fetch request
 * @returns {Promise<Response>} The default response
 */
async function handleDefaultRequest(request) {
  try {
    const networkResponse = await fetch(request);
    
    if (networkResponse.ok && request.method === 'GET') {
      const cache = await caches.open(DYNAMIC_CACHE_NAME);
      await cache.put(request, networkResponse.clone());
    }
    
    return networkResponse;
    
  } catch (error) {
    console.error('[ServiceWorker] Default request failed:', error);
    
    if (request.method === 'GET') {
      const cachedResponse = await caches.match(request);
      if (cachedResponse) {
        return cachedResponse;
      }
    }
    
    return new Response('Request failed', { status: 503 });
  }
}

/**
 * Determines if a request can be queued for background sync
 * @param {Request} request - The fetch request
 * @returns {boolean} True if the request can be synced
 */
function isSyncableRequest(request) {
  const url = new URL(request.url);
  return SYNC_ENDPOINTS.some(endpoint => url.pathname.startsWith(endpoint));
}

/**
 * Queues a request for background synchronization
 * @param {Request} request - The request to queue
 * @returns {Promise<void>}
 */
async function queueRequestForSync(request) {
  try {
    const requestData = {
      url: request.url,
      method: request.method,
      headers: [...request.headers.entries()],
      body: await request.text(),
      timestamp: Date.now()
    };
    
    // Store in IndexedDB or localStorage for background sync
    const db = await openIndexedDB();
    await storeRequestForSync(db, requestData);
    
    // Register background sync
    await self.registration.sync.register(BACKGROUND_SYNC_TAG);
    
    console.log('[ServiceWorker] Request queued for background sync:', request.url);
    
  } catch (error) {
    console.error('[ServiceWorker] Failed to queue request for sync:', error);
  }
}

/**
 * Processes pending requests during background sync
 * @returns {Promise<void>}
 */
async function processPendingRequests() {
  try {
    const db = await openIndexedDB();
    const pendingRequests = await getPendingRequests(db);
    
    for (const requestData of pendingRequests) {
      try {
        const request = new Request(requestData.url, {
          method: requestData.method,
          headers: requestData.headers,
          body: requestData.body
        });
        
        const response = await fetch(request);
        
        if (response.ok) {
          await removeRequestFromSync(db, requestData.id);
          console.log('[ServiceWorker] Synced request successfully:', requestData.url);
        } else {
          console.warn('[ServiceWorker] Sync request failed:', requestData.url, response.status);
        }
        
      } catch (error) {
        console.error('[ServiceWorker] Error processing sync request:', error);
      }
    }
    
  } catch (error) {
    console.error('[ServiceWorker] Error processing pending requests:', error);
  }
}

/**
 * Creates notification options based on notification data
 * @param {Object} notificationData - The notification data
 * @returns {Object} Notification options
 */
function createNotificationOptions(notificationData) {
  const baseOptions = {
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-72x72.png',
    requireInteraction: false,
    silent: false,
    data: notificationData
  };
  
  // Configure based on notification type
  switch (notificationData.type) {
    case 'transaction':
      return {
        ...baseOptions,
        body: notificationData.body || 'New transaction processed',
        actions: [
          { action: 'view', title: 'View Transaction' },
          { action: 'dismiss', title: 'Dismiss' }
        ],
        tag: 'transaction',
        requireInteraction: true
      };
      
    case 'account':
      return {
        ...baseOptions,
        body: notificationData.body || 'Account information updated',
        actions: [
          { action: 'view', title: 'View Account' },
          { action: 'dismiss', title: 'Dismiss' }
        ],
        tag: 'account'
      };
      
    case 'security':
      return {
        ...baseOptions,
        body: notificationData.body || 'Security alert',
        actions: [
          { action: 'view', title: 'View Details' },
          { action: 'dismiss', title: 'Dismiss' }
        ],
        tag: 'security',
        requireInteraction: true,
        vibrate: [200, 100, 200]
      };
      
    default:
      return {
        ...baseOptions,
        body: notificationData.body || 'New notification',
        actions: [
          { action: 'view', title: 'View' },
          { action: 'dismiss', title: 'Dismiss' }
        ]
      };
  }
}

/**
 * Determines the target URL for notification clicks
 * @param {Object} notificationData - The notification data
 * @returns {string} Target URL
 */
function determineTargetUrl(notificationData) {
  switch (notificationData.type) {
    case 'transaction':
      return notificationData.transactionId 
        ? `/transactions/${notificationData.transactionId}`
        : '/transactions';
        
    case 'account':
      return notificationData.accountId
        ? `/accounts/${notificationData.accountId}`
        : '/accounts';
        
    case 'security':
      return '/security';
      
    default:
      return '/';
  }
}

/**
 * Cleans up expired cache entries
 * @returns {Promise<void>}
 */
async function cleanupExpiredCaches() {
  const cacheNames = [API_CACHE_NAME, DYNAMIC_CACHE_NAME];
  
  for (const cacheName of cacheNames) {
    try {
      const cache = await caches.open(cacheName);
      const keys = await cache.keys();
      
      let count = 0;
      for (const request of keys) {
        const response = await cache.match(request);
        const cacheTime = response.headers.get('sw-cache-time');
        
        if (cacheTime) {
          const age = Date.now() - parseInt(cacheTime);
          if (age > CACHE_EXPIRATION_TIME) {
            await cache.delete(request);
            count++;
          }
        }
        
        // Limit cache size
        if (keys.length > MAX_CACHE_SIZE) {
          await cache.delete(request);
          count++;
        }
      }
      
      if (count > 0) {
        console.log(`[ServiceWorker] Cleaned up ${count} expired entries from ${cacheName}`);
      }
      
    } catch (error) {
      console.error(`[ServiceWorker] Error cleaning cache ${cacheName}:`, error);
    }
  }
}

/**
 * Generates offline fallback page HTML
 * @returns {string} Offline page HTML
 */
function generateOfflinePage() {
  return `
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>CardDemo - Offline</title>
      <style>
        body {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          margin: 0;
          padding: 20px;
          background-color: #f5f5f5;
          color: #333;
        }
        .container {
          max-width: 600px;
          margin: 0 auto;
          background: white;
          padding: 40px;
          border-radius: 8px;
          box-shadow: 0 2px 4px rgba(0,0,0,0.1);
          text-align: center;
        }
        .icon {
          width: 64px;
          height: 64px;
          margin: 0 auto 20px;
          background: #1976d2;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;
          font-size: 24px;
        }
        h1 {
          color: #1976d2;
          margin-bottom: 10px;
        }
        p {
          color: #666;
          line-height: 1.6;
        }
        .retry-button {
          background: #1976d2;
          color: white;
          border: none;
          padding: 12px 24px;
          border-radius: 4px;
          cursor: pointer;
          font-size: 16px;
          margin-top: 20px;
        }
        .retry-button:hover {
          background: #1565c0;
        }
      </style>
    </head>
    <body>
      <div class="container">
        <div class="icon">📱</div>
        <h1>You're Offline</h1>
        <p>It looks like you're not connected to the internet. Some features of CardDemo may not be available right now.</p>
        <p>Don't worry - any changes you make will be saved and synced when you're back online.</p>
        <button class="retry-button" onclick="window.location.reload()">Try Again</button>
      </div>
    </body>
    </html>
  `;
}

/**
 * Opens IndexedDB for background sync storage
 * @returns {Promise<IDBDatabase>} IndexedDB instance
 */
async function openIndexedDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open('CardDemoSyncDB', 1);
    
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
    
    request.onupgradeneeded = (event) => {
      const db = event.target.result;
      if (!db.objectStoreNames.contains('syncRequests')) {
        db.createObjectStore('syncRequests', { keyPath: 'id', autoIncrement: true });
      }
    };
  });
}

/**
 * Stores a request for background sync
 * @param {IDBDatabase} db - IndexedDB instance
 * @param {Object} requestData - Request data to store
 * @returns {Promise<void>}
 */
async function storeRequestForSync(db, requestData) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(['syncRequests'], 'readwrite');
    const store = transaction.objectStore('syncRequests');
    const request = store.add(requestData);
    
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve();
  });
}

/**
 * Gets pending requests from IndexedDB
 * @param {IDBDatabase} db - IndexedDB instance
 * @returns {Promise<Array>} Array of pending requests
 */
async function getPendingRequests(db) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(['syncRequests'], 'readonly');
    const store = transaction.objectStore('syncRequests');
    const request = store.getAll();
    
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}

/**
 * Removes a request from sync storage
 * @param {IDBDatabase} db - IndexedDB instance
 * @param {number} id - Request ID to remove
 * @returns {Promise<void>}
 */
async function removeRequestFromSync(db, id) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(['syncRequests'], 'readwrite');
    const store = transaction.objectStore('syncRequests');
    const request = store.delete(id);
    
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve();
  });
}

// Register event listeners
self.addEventListener('install', install);
self.addEventListener('activate', activate);
self.addEventListener('fetch', fetch);
self.addEventListener('sync', sync);
self.addEventListener('push', push);
self.addEventListener('notificationclick', notificationclick);

// Log service worker registration
console.log('[ServiceWorker] CardDemo service worker loaded');