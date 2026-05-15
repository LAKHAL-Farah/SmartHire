import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { DisplayUser, UserManagementService } from '../../../service/user-management.service';

@Component({
  selector: 'app-edit-user-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule
  ],
  templateUrl: './edit-user-modal.component.html',
  styleUrl: './edit-user-modal.component.scss'
})
export class EditUserModalComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<EditUserModalComponent>);
  private data = inject(MAT_DIALOG_DATA);
  private userService = inject(UserManagementService);
  private fb = inject(FormBuilder);

  user: DisplayUser = this.data.user;
  form!: FormGroup;
  isSubmitting = false;
  errorMessage = '';
  roles = [
    { value: 'candidate', label: 'Candidate' },
    { value: 'recruiter', label: 'Recruiter' }
  ];

  get userGradient(): string {
    return this.user.role === 'Candidate' ? 'linear-gradient(135deg,#2ee8a5,#0ea5e9)' :
           'linear-gradient(135deg,#a78bfa,#7c3aed)';
  }

  ngOnInit(): void {
    this.initializeForm();
  }

  initializeForm(): void {
    const roleValue = this.user.role === 'Candidate' ? 'candidate' : 'recruiter';

    this.form = this.fb.group({
      email: [this.user.email, [Validators.required, Validators.email]],
      password: [''],
      roleName: [roleValue, Validators.required]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.errorMessage = 'Please fill in all required fields correctly';
      return;
    }

    this.isSubmitting = true;
    const formData = {
      email: this.form.value.email,
      roleName: this.form.value.roleName
    };

    // Only add password if it's provided
    if (this.form.value.password) {
      (formData as any).password = this.form.value.password;
    }

    this.userService.updateUser(this.user.id, formData).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        this.dialogRef.close({ success: true, user: response });
      },
      error: (error) => {
        this.isSubmitting = false;
        this.errorMessage = error.message || 'Failed to update user';
        console.error('Error updating user:', error);
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close({ success: false });
  }
}
