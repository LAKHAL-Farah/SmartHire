// user-management.component.ts
import { Component, OnInit, inject, ElementRef, ViewChild, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { finalize } from 'rxjs/operators';
import { DisplayUser, UserManagementService } from '../../service/user-management.service';
import { EditUserModalComponent } from './edit-user-modal/edit-user-modal.component';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss'
})
export class UserManagementComponent implements OnInit {
  private userService = inject(UserManagementService);
  private dialog = inject(MatDialog);
  
  /* ── Filters ── */
  searchQuery = '';
  roleFilter = 'All';
  statusFilter = 'All';
  planFilter = 'All';
  roles = ['All', 'Candidate', 'Recruiter', 'Admin'];
  statuses = ['All', 'Active', 'Suspended', 'Inactive', 'Pending'];
  plans = ['All', 'Free', 'Premium', 'Recruiter'];

  /* ── Data ── */
  users: DisplayUser[] = [];
  isLoading = true;
  errorMessage: string | null = null;

  /* ── Pagination ── */
  currentPage = 1;
  pageSize = 15;
  
  get totalPages(): number { 
    return Math.ceil(this.filteredUsers.length / this.pageSize); 
  }
  
  get paginatedUsers(): DisplayUser[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredUsers.slice(start, start + this.pageSize);
  }

  /* ── Selection / Bulk ── */
  selectedIds = new Set<string>();
  allChecked = false;

  /* ── Drawer ── */
  drawerOpen = false;
  drawerUser: DisplayUser | null = null;

  /* ── Action dropdown ── */
  openMenuId: string | null = null;
  menuPosition: 'below' | 'above' = 'below';

  /* ── Lifecycle ── */
  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading = true;
    this.errorMessage = null;
    
    this.userService.getAllUsers()
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (apiUsers) => {
          this.users = apiUsers.map(user => this.userService.transformToDisplayUser(user));
          console.log('Users loaded:', this.users.length);
        },
        error: (error) => {
          this.errorMessage = error.message;
          console.error('Error loading users:', error);
        }
      });
  }

  /* ── Filtered list ── */
  get filteredUsers(): DisplayUser[] {
    return this.users.filter(u => {
      const matchSearch = !this.searchQuery ||
        u.name.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        u.email.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        u.headline?.toLowerCase().includes(this.searchQuery.toLowerCase());
      
      const matchRole = this.roleFilter === 'All' || u.role === this.roleFilter;
      const matchStatus = this.statusFilter === 'All' || u.status === this.statusFilter;
      const matchPlan = this.planFilter === 'All' || u.plan === this.planFilter;
      
      return matchSearch && matchRole && matchStatus && matchPlan;
    });
  }

  /* ── Helpers ── */
  getRoleClass(role: string): string {
    return role === 'Candidate' ? 'pill--teal' : 
           role === 'Recruiter' ? 'pill--purple' : 
           'pill--red';
  }
  
  getPlanClass(plan: string): string {
    return plan === 'Free' ? 'pill--gray' : 
           plan === 'Premium' ? 'pill--amber' : 
           'pill--purple';
  }
  
  getStatusClass(status: string): string {
    return status === 'Active' ? 'badge--green' : 
           status === 'Suspended' ? 'badge--red' : 
           'badge--yellow';
  }
  
  avatarGradient(role: string): string {
    return role === 'Candidate' ? 'linear-gradient(135deg,#2ee8a5,#0ea5e9)' :
           role === 'Recruiter' ? 'linear-gradient(135deg,#a78bfa,#7c3aed)' :
           'linear-gradient(135deg,#f87171,#ef4444)';
  }

  /* ── Checkbox logic ── */
  toggleAll(): void {
    if (this.allChecked) {
      this.selectedIds.clear();
      this.allChecked = false;
    } else {
      this.paginatedUsers.forEach(u => this.selectedIds.add(u.id));
      this.allChecked = true;
    }
  }
  
  toggleRow(id: string): void {
    this.selectedIds.has(id) ? this.selectedIds.delete(id) : this.selectedIds.add(id);
    this.allChecked = this.paginatedUsers.every(u => this.selectedIds.has(u.id));
  }
  
  isSelected(id: string): boolean { 
    return this.selectedIds.has(id); 
  }

  clearSelection(): void { 
    this.selectedIds.clear(); 
    this.allChecked = false; 
  }

  /* ── Pagination ── */
  goPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) { 
      this.currentPage = page; 
      this.allChecked = false; 
    }
  }
  
  get pageNumbers(): number[] {
    const pages: number[] = [];
    const total = this.totalPages;
    const current = this.currentPage;
    const maxVisible = 5;
    
    if (total <= maxVisible) {
      for (let i = 1; i <= total; i++) pages.push(i);
    } else {
      if (current <= 3) {
        for (let i = 1; i <= 4; i++) pages.push(i);
        pages.push(-1);
        pages.push(total);
      } else if (current >= total - 2) {
        pages.push(1);
        pages.push(-1);
        for (let i = total - 3; i <= total; i++) pages.push(i);
      } else {
        pages.push(1);
        pages.push(-1);
        for (let i = current - 1; i <= current + 1; i++) pages.push(i);
        pages.push(-1);
        pages.push(total);
      }
    }
    return pages;
  }

  /* ── Drawer ── */
  openDrawer(user: DisplayUser): void { 
    this.drawerUser = user; 
    this.drawerOpen = true; 
    this.openMenuId = null; 
  }
  
  closeDrawer(): void { 
    this.drawerOpen = false; 
    this.drawerUser = null; 
  }

  /* ── Row actions menu ── */
  toggleMenu(id: string, event: Event): void {
    event.stopPropagation();
    
    // Calculate menu position
    const button = event.target as HTMLElement;
    const rect = button.getBoundingClientRect();
    const menuHeight = 160; // Approximate height of the menu
    const spaceBelow = window.innerHeight - rect.bottom;
    const spaceAbove = rect.top;
    
    this.menuPosition = spaceBelow >= menuHeight || spaceBelow > spaceAbove ? 'below' : 'above';
    
    this.openMenuId = this.openMenuId === id ? null : id;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    this.openMenuId = null;
  }

  /* ── Actions ── */
  exportCSV(): void {
    const headers = ['Name', 'Email', 'Role', 'Plan', 'Status', 'Joined', 'Last Active', 'Location', 'Headline'];
    const data = this.filteredUsers.map(u => [
      u.name,
      u.email,
      u.role,
      u.plan,
      u.status,
      u.joined,
      u.lastActive,
      u.location,
      u.headline
    ]);
    
    const csvContent = [headers, ...data]
      .map(row => row.map(cell => `"${cell}"`).join(','))
      .join('\n');
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', 'users_export.csv');
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  editUser(user: DisplayUser): void {
    const dialogRef = this.dialog.open(EditUserModalComponent, {
      width: '500px',
      maxWidth: '90vw',
      maxHeight: '85vh',
      panelClass: ['custom-dialog'],
      data: { user },
      backdropClass: 'custom-backdrop'
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.success) {
        // Update the user in the local list
        const index = this.users.findIndex(u => u.id === user.id);
        if (index !== -1) {
          // Reload the user data from the updated response
          const updatedApiUser = result.user;
          this.users[index] = this.userService.transformToDisplayUser(updatedApiUser);
          
          // Update drawer if it's open for this user
          if (this.drawerUser?.id === user.id) {
            this.drawerUser = this.users[index];
          }
        }
        this.closeDrawer();
        this.openMenuId = null;
      }
    });
  }

  resetPassword(user: DisplayUser): void {
    console.log('Reset password for:', user);
    // Implement password reset
  }

  suspendUser(user: DisplayUser): void {
    console.log('Suspend user:', user);
    // Implement suspend logic
  }

  reactivateUser(user: DisplayUser): void {
    console.log('Reactivate user:', user);
    // Implement reactivate logic
  }

  deleteUser(user: DisplayUser): void {
    const confirmed = confirm(`Are you sure you want to delete ${user.name}? This action cannot be undone.`);
    
    if (!confirmed) {
      return;
    }

    this.userService.deleteUser(user.id).subscribe({
      next: () => {
        console.log('User deleted successfully');
        // Remove user from the local list without refreshing the page
        this.users = this.users.filter(u => u.id !== user.id);
        this.selectedIds.delete(user.id);
        this.closeDrawer();
        this.openMenuId = null;
      },
      error: (error) => {
        console.error('Error deleting user:', error);
        alert('Failed to delete user. Please try again.');
      }
    });
  
  }}