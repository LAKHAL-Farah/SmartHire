import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { filter } from 'rxjs/operators';

export interface SessionEvent {
  eventType: string;
  sessionId: number;
  payload: any;
  message: string;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private readonly connectedSubject = new BehaviorSubject<boolean>(false);
  private readonly messagesSubject = new Subject<SessionEvent>();
  private readonly subscriptions = new Map<string, StompSubscription>();
  private readonly desiredDestinations = new Set<string>();

  readonly isConnected$ = this.connectedSubject.asObservable();

  private readonly client: Client;

  constructor() {
    this.client = new Client({
      webSocketFactory: () => new WebSocket(this.resolveNativeWebSocketUrl()),
      reconnectDelay: 5000,
      onConnect: () => {
        this.connectedSubject.next(true);
        this.resubscribeAll();
      },
      onDisconnect: () => {
        this.connectedSubject.next(false);
      },
      onStompError: () => {
        this.connectedSubject.next(false);
      },
    });
  }

  connect(): void {
    if (!this.client.active) {
      try {
        this.client.activate();
      } catch {
        this.connectedSubject.next(false);
      }
    }
  }

  subscribeToSession(sessionId: number): Observable<SessionEvent> {
    const destination = this.destinationFor(sessionId);
    this.desiredDestinations.add(destination);
    this.connect();
    this.ensureSubscription(destination);

    return this.messagesSubject.asObservable().pipe(
      filter((event) => event.sessionId === sessionId)
    );
  }

  onEvent(sessionId: number, eventType: string): Observable<SessionEvent> {
    return this.subscribeToSession(sessionId).pipe(
      filter((event) => event.eventType === eventType)
    );
  }

  send(destination: string, body: unknown): void {
    if (!this.client.connected) {
      return;
    }

    this.client.publish({
      destination: `/app${destination}`,
      body: JSON.stringify(body),
    });
  }

  unsubscribeFromSession(sessionId: number): void {
    const destination = this.destinationFor(sessionId);
    this.desiredDestinations.delete(destination);

    const active = this.subscriptions.get(destination);
    if (active) {
      active.unsubscribe();
      this.subscriptions.delete(destination);
    }
  }

  disconnect(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();
    this.desiredDestinations.clear();

    if (this.client.active) {
      this.client.deactivate();
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private resubscribeAll(): void {
    this.desiredDestinations.forEach((destination) => this.ensureSubscription(destination));
  }

  private ensureSubscription(destination: string): void {
    if (!this.client.connected || this.subscriptions.has(destination)) {
      return;
    }

    const sub = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const event = JSON.parse(message.body) as SessionEvent;
        this.messagesSubject.next(event);
      } catch {
        // Ignore malformed event payloads.
      }
    });

    this.subscriptions.set(destination, sub);
  }

  private destinationFor(sessionId: number): string {
    return `/topic/session/${sessionId}`;
  }

  private resolveNativeWebSocketUrl(): string {
    return '/interview-service/ws/websocket';
  }
}
