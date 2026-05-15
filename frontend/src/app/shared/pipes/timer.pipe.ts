import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'timerFormat',
  pure: true,
  standalone: true,
})
export class TimerPipe implements PipeTransform {
  transform(totalSeconds: number): string {
    const safeSeconds = Number.isFinite(totalSeconds) ? Math.max(0, Math.floor(totalSeconds)) : 0;
    const m = Math.floor(safeSeconds / 60).toString().padStart(2, '0');
    const s = (safeSeconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }
}
