import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, map, Observable, of, switchMap } from 'rxjs';
import {
  CreateStepResourceRequest,
  RoadmapApiService,
  RoadmapNodeDto,
  RoadmapVisualResponse,
  StepResponse,
  StepResourceDto,
} from '../../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from '../roadmap-user-context';

type UiStatus = 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';

interface NodeQuizStorageState {
  passedByStep: Record<string, boolean>;
}

interface StepDetailModel {
  id: number;
  nodeId: number;
  resourceStepId: number;
  completionType: 'node' | 'step';
  stepOrder: number;
  title: string;
  description: string;
  objective: string;
  status: UiStatus;
  estimatedDays: number;
  technologies: string[];
}

@Component({
  selector: 'app-step-detail',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './step-detail.component.html',
  styleUrl: './step-detail.component.scss',
})
export class StepDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly roadmapApi = inject(RoadmapApiService);
  private readonly fb = inject(FormBuilder);

  loading = signal(false);
  resourcesLoading = signal(false);
  completing = signal(false);
  errorMessage = signal<string | null>(null);

  readonly roadmapId = signal<number | null>(null);
  readonly stepId = signal<number | null>(null);
  readonly userId = signal<number | null>(null);

  readonly step = signal<StepDetailModel | null>(null);
  readonly resources = signal<StepResourceDto[]>([]);
  readonly quizPassedForStep = signal(false);

  readonly resourceForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120)]],
    url: ['', [Validators.required]],
    type: ['ARTICLE', [Validators.required]],
    provider: ['OTHER', [Validators.required]],
    durationHours: [1, [Validators.min(0.25)]],
  });

  readonly progressPercent = computed(() => {
    const status = this.step()?.status;
    if (status === 'COMPLETED') {
      return 100;
    }
    if (status === 'IN_PROGRESS') {
      return 60;
    }
    if (status === 'AVAILABLE') {
      return 20;
    }
    if (status === 'SKIPPED') {
      return 100;
    }
    return 0;
  });

  readonly statusLabel = computed(() => {
    const status = this.step()?.status;
    if (status === 'IN_PROGRESS') {
      return 'In Progress';
    }
    if (status === 'COMPLETED') {
      return 'Completed';
    }
    if (status === 'AVAILABLE') {
      return 'Available';
    }
    if (status === 'SKIPPED') {
      return 'Skipped';
    }
    return 'Locked';
  });

  ngOnInit(): void {
    const parsedStepId = Number(this.route.snapshot.paramMap.get('stepId'));
    if (!Number.isFinite(parsedStepId) || parsedStepId <= 0) {
      this.errorMessage.set('Invalid step id in route.');
      return;
    }

    this.stepId.set(parsedStepId);
    this.userId.set(resolveRoadmapUserId());
    if (!this.userId()) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }
    this.loadStepDetails(parsedStepId);
  }

  markComplete(): void {
    const current = this.step();
    const userId = this.userId();
    if (!current || !userId || current.status === 'COMPLETED') {
      return;
    }

    if (current.status === 'LOCKED') {
      this.errorMessage.set('This node is locked. Complete required previous nodes first.');
      return;
    }

    if (current.status !== 'AVAILABLE' && current.status !== 'IN_PROGRESS') {
      this.errorMessage.set('This step is not available for completion right now.');
      return;
    }

    if (!this.quizPassedForStep()) {
      this.errorMessage.set('Pass the node quiz from the roadmap page before marking this step complete.');
      return;
    }

    this.completing.set(true);
    this.errorMessage.set(null);

    const completion$: Observable<RoadmapVisualResponse | null> =
      current.completionType === 'node'
        ? this.roadmapApi.completeNode(current.nodeId, userId)
        : this.roadmapApi
            .completeRoadmapStep(current.nodeId, userId)
            .pipe(map(() => null));

    completion$
      .pipe(finalize(() => this.completing.set(false)))
      .subscribe({
        next: (graph: RoadmapVisualResponse | null) => {
          if (graph) {
            const nextNode =
              graph.nodes.find((n) => n.id === current.nodeId) ??
              graph.nodes.find((n) => n.stepOrder === current.stepOrder);
            if (nextNode) {
              this.step.set(this.mapNode(nextNode));
              return;
            }
          }

          this.step.update((prev) =>
            prev
              ? {
                  ...prev,
                  status: 'COMPLETED',
                }
              : null
          );
        },
        error: () => {
          this.errorMessage.set('Could not mark this step as complete right now.');
        },
      });
  }

  addCustomResource(): void {
    const currentStep = this.step();
    if (this.resourceForm.invalid) {
      this.resourceForm.markAllAsTouched();
      return;
    }

    if (!currentStep?.resourceStepId) {
      this.errorMessage.set('Unable to attach resource to this roadmap step.');
      return;
    }

    const formValue = this.resourceForm.getRawValue();
    const payload: CreateStepResourceRequest = {
      title: formValue.title,
      url: formValue.url,
      type: formValue.type,
      provider: formValue.provider,
      durationHours: formValue.durationHours,
      isFree: true,
    };

    this.resourcesLoading.set(true);
    this.roadmapApi
      .addStepResource(currentStep.resourceStepId, payload)
      .pipe(finalize(() => this.resourcesLoading.set(false)))
      .subscribe({
        next: (created) => {
          this.resources.update((current) => [created, ...current]);
          this.resourceForm.reset({
            title: '',
            url: '',
            type: 'ARTICLE',
            provider: 'OTHER',
            durationHours: 1,
          });
        },
        error: () => {
          this.errorMessage.set('Could not save this custom resource.');
        },
      });
  }

  trackResource(_index: number, resource: StepResourceDto): string | number {
    return resource.id ?? resource.url;
  }

  private loadStepDetails(stepId: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.roadmapApi
      .getUserRoadmap(this.userId()!)
      .pipe(
        switchMap((roadmap) => {
          this.roadmapId.set(roadmap.id);
          return this.resolveStepModel(roadmap.id, stepId);
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (resolvedStep) => {
          if (!resolvedStep) {
            this.step.set(null);
            this.resources.set([]);
            this.quizPassedForStep.set(false);
            this.errorMessage.set('Step not found in your current roadmap.');
            return;
          }

          this.step.set(resolvedStep);
          this.syncQuizGateForStep(resolvedStep);
          this.loadStepResources(resolvedStep.resourceStepId, resolvedStep.title);
        },
        error: () => {
          this.errorMessage.set('Unable to load step details from API.');
          this.resources.set([]);
          this.quizPassedForStep.set(false);
        },
      });
  }

  private resolveStepModel(
    roadmapId: number,
    routeStepId: number
  ): Observable<StepDetailModel | null> {
    return this.roadmapApi.getRoadmapGraph(roadmapId).pipe(
      switchMap((graph) => {
        const node =
          graph.nodes.find((n) => n.id === routeStepId) ??
          graph.nodes.find((n) => n.stepOrder === routeStepId);

        if (node) {
          return of(this.mapNode(node));
        }

        return this.resolveCrudStepModel(roadmapId, routeStepId);
      }),
      catchError(() => this.resolveCrudStepModel(roadmapId, routeStepId))
    );
  }

  private resolveCrudStepModel(
    roadmapId: number,
    routeStepId: number
  ): Observable<StepDetailModel | null> {
    return this.roadmapApi.getRoadmapStepsByRoadmapId(roadmapId).pipe(
      map((steps) => {
        const sorted = (steps ?? []).slice().sort((a, b) => a.stepOrder - b.stepOrder);
        const byId = sorted.find((step) => step.id === routeStepId);
        const byOrder = sorted.find((step) => step.stepOrder === routeStepId);
        const target = byId ?? byOrder ?? null;
        return target ? this.mapCrudStep(target) : null;
      }),
      catchError(() => of(null))
    );
  }

  private loadStepResources(stepRefId: number, topic: string): void {
    this.resourcesLoading.set(true);

    this.roadmapApi
      .getStepResourcesByStep(stepRefId)
      .pipe(
        switchMap((existing) =>
          existing.length > 0
            ? of(existing)
            : this.roadmapApi.syncStepResources(stepRefId).pipe(
                catchError(() => of(void 0)),
                switchMap(() => this.roadmapApi.getStepResourcesByStep(stepRefId)),
                catchError(() => this.roadmapApi.getStepResources(topic)),
              )
        ),
        catchError(() => this.roadmapApi.getStepResources(topic)),
        finalize(() => this.resourcesLoading.set(false))
      )
      .subscribe((resources) => {
        this.resources.set(resources);
      });
  }

  private mapCrudStep(step: StepResponse): StepDetailModel {
    return {
      id: step.id,
      nodeId: step.id,
      resourceStepId: step.id,
      completionType: 'step',
      stepOrder: step.stepOrder,
      title: step.title,
      description: step.objective || 'No description provided yet.',
      objective: step.objective || 'No objective provided yet.',
      status: this.normalizeStatus(step.status),
      estimatedDays: step.estimatedDays || 0,
      technologies: [],
    };
  }

  private mapNode(node: RoadmapNodeDto): StepDetailModel {
    const technologyList = (node.technologies || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);

    return {
      id: node.id,
      nodeId: node.id,
      resourceStepId: node.id,
      completionType: 'node',
      stepOrder: node.stepOrder,
      title: node.title,
      description: node.description || 'No description provided yet.',
      objective: node.objective || 'No objective provided yet.',
      status: this.normalizeStatus(node.status),
      estimatedDays: node.estimatedDays,
      technologies: technologyList,
    };
  }

  private normalizeStatus(status: string | undefined): UiStatus {
    const normalized = (status || '').toUpperCase();
    if (normalized === 'IN_PROGRESS') {
      return 'IN_PROGRESS';
    }
    if (normalized === 'AVAILABLE') {
      return 'AVAILABLE';
    }
    if (normalized === 'COMPLETED' || normalized === 'DONE') {
      return 'COMPLETED';
    }
    if (normalized === 'SKIPPED') {
      return 'SKIPPED';
    }
    return 'LOCKED';
  }

  private syncQuizGateForStep(step: StepDetailModel | null): void {
    if (!step) {
      this.quizPassedForStep.set(false);
      return;
    }

    if (step.status === 'COMPLETED' || step.status === 'SKIPPED') {
      this.quizPassedForStep.set(true);
      return;
    }

    const quizState = this.readQuizState();
    this.quizPassedForStep.set(quizState.passedByStep[this.toStepKey(step.stepOrder)] === true);
  }

  private readQuizState(): NodeQuizStorageState {
    const storageKey = this.quizStorageKey();
    if (!storageKey || typeof localStorage === 'undefined') {
      return { passedByStep: {} };
    }

    try {
      const raw = localStorage.getItem(storageKey);
      if (!raw) {
        return { passedByStep: {} };
      }

      const parsed = JSON.parse(raw) as Partial<NodeQuizStorageState>;
      const passedByStep = parsed.passedByStep;

      if (!passedByStep || typeof passedByStep !== 'object') {
        return { passedByStep: {} };
      }

      return {
        passedByStep: passedByStep as Record<string, boolean>,
      };
    } catch {
      return { passedByStep: {} };
    }
  }

  private quizStorageKey(): string | null {
    const userId = this.userId();
    const roadmapId = this.roadmapId();
    if (!userId || !roadmapId) {
      return null;
    }

    return `smarthire-node-quiz:${userId}:${roadmapId}`;
  }

  private toStepKey(stepOrder: number): string {
    return `step-${stepOrder}`;
  }
}
