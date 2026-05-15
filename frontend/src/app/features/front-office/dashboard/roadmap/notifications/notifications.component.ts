import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import {
  NotificationDto,
  RoadmapApiService,
} from '../../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from '../roadmap-user-context';

@Component({
  selector: 'app-roadmap-notifications',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements OnInit {
  private readonly roadmapApi = inject(RoadmapApiService);

  loading = signal(false);
  errorMessage = signal<string | null>(null);

  userId = signal<number | null>(null);
  roadmapId = signal<number | null>(null);

  notifications = signal<NotificationDto[]>([]);
  unreadCount = signal(0);

  actingNotificationId = signal<number | null>(null);
  markingAll = signal(false);

  ngOnInit(): void {
    this.userId.set(resolveRoadmapUserId());
    if (!this.userId()) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }
    this.loadNotifications();
  }

  trackNotification(_index: number, notification: NotificationDto): number {
    return notification.id;
  }

  markAsRead(notification: NotificationDto): void {
    if (notification.isRead || this.actingNotificationId() === notification.id) {
      return;
    }

    this.actingNotificationId.set(notification.id);

    this.roadmapApi
      .markNotificationAsRead(notification.id)
      .pipe(finalize(() => this.actingNotificationId.set(null)))
      .subscribe({
        next: (updated) => {
          this.notifications.update((list) =>
            list.map((item) => (item.id === notification.id ? updated : item))
          );
          this.unreadCount.update((count) => (count > 0 ? count - 1 : 0));
        },
        error: () => {
          this.errorMessage.set('Could not mark this notification as read.');
        },
      });
  }

  markAllAsRead(): void {
    const userId = this.userId();
    if (this.markingAll()) {
      return;
    }
    if (!userId) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }

    this.markingAll.set(true);
    this.roadmapApi
      .markAllAsRead(userId)
      .pipe(finalize(() => this.markingAll.set(false)))
      .subscribe({
        next: () => {
          this.notifications.update((list) => list.map((item) => ({ ...item, isRead: true })));
          this.unreadCount.set(0);
        },
        error: () => {
          this.errorMessage.set('Unable to mark all notifications as read.');
        },
      });
  }

  deleteNotification(notification: NotificationDto): void {
    if (this.actingNotificationId() === notification.id) {
      return;
    }

    this.actingNotificationId.set(notification.id);
    this.roadmapApi
      .deleteNotification(notification.id)
      .pipe(finalize(() => this.actingNotificationId.set(null)))
      .subscribe({
        next: () => {
          this.notifications.update((list) => list.filter((item) => item.id !== notification.id));
          if (!notification.isRead) {
            this.unreadCount.update((count) => (count > 0 ? count - 1 : 0));
          }
        },
        error: () => {
          this.errorMessage.set('Could not delete this notification.');
        },
      });
  }

  asTypeClass(type: string): string {
    return `type-${(type || 'info').toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
  }

  private loadNotifications(): void {
    const userId = this.userId();
    if (!userId) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.roadmapApi
      .getUserRoadmap(userId)
      .pipe(
        catchError(() => of(null)),
        switchMap((roadmap) => {
          this.roadmapId.set(roadmap?.id ?? null);

          const notifications$ = roadmap
            ? this.roadmapApi
                .getRoadmapNotifications(roadmap.id, userId)
                .pipe(catchError(() => this.roadmapApi.getUserNotifications(userId)))
            : this.roadmapApi.getUserNotifications(userId);

          return forkJoin({
            notifications: notifications$.pipe(catchError(() => of([]))),
            unread: this.roadmapApi
              .getUnreadCount(userId)
              .pipe(catchError(() => of({ count: 0 }))),
          });
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: ({ notifications, unread }) => {
          const sorted = notifications
            .slice()
            .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
          this.notifications.set(sorted);
          this.unreadCount.set(unread.count ?? 0);
        },
        error: () => {
          this.errorMessage.set('Unable to load notifications right now.');
        },
      });
  }
}
