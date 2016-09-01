package com.expensemanager.app.expense;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.expensemanager.app.R;
import com.expensemanager.app.expense.category_picker.CategoryPickerFragment;
import com.expensemanager.app.expense.photo.ExpensePhotoAdapter;
import com.expensemanager.app.helpers.DatePickerFragment;
import com.expensemanager.app.helpers.Helpers;
import com.expensemanager.app.helpers.TimePickerFragment;
import com.expensemanager.app.main.BaseActivity;
import com.expensemanager.app.models.Category;
import com.expensemanager.app.models.Expense;
import com.expensemanager.app.models.User;
import com.expensemanager.app.service.ExpenseBuilder;
import com.expensemanager.app.service.PermissionsManager;
import com.expensemanager.app.service.SyncExpense;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import bolts.Continuation;
import bolts.Task;
import butterknife.BindView;
import butterknife.ButterKnife;
import de.hdodenhof.circleimageview.CircleImageView;

public class NewExpenseActivity extends BaseActivity {
    private static final String TAG = NewExpenseActivity.class.getSimpleName();

    public static final String DATE_PICKER = "date_picker";
    public static final String TIME_PICKER = "time_picker";
    public static final String NEW_PHOTO = "Take a photo";
    public static final String LIBRARY_PHOTO = "Choose from library";
    public static final int SELECT_PICTURE_REQUEST_CODE = 1;

    private ArrayList<byte[]> photoList;
    private ExpensePhotoAdapter expensePhotoAdapter;
    private Uri outputFileUri;
    private AlertDialog.Builder choosePhotoSource;
    private Calendar calendar;
    private Expense expense;
    private Category category;

    @BindView(R.id.toolbar_id) Toolbar toolbar;
    @BindView(R.id.toolbar_back_image_view_id) ImageView backImageView;
    @BindView(R.id.toolbar_title_text_view_id) TextView titleTextView;
    @BindView(R.id.toolbar_edit_text_view_id) TextView editTextView;
    @BindView(R.id.toolbar_save_text_view_id) TextView saveTextView;
    @BindView(R.id.new_expense_activity_amount_text_view_id) TextView amountTextView;
    @BindView(R.id.new_expense_activity_note_text_view_id) TextView noteTextView;
    @BindView(R.id.new_expense_activity_grid_view_id) GridView photoGridView;
    @BindView(R.id.progress_bar_id) ProgressBar progressBar;
    @BindView(R.id.new_expense_activity_category_hint_text_view_id) TextView categoryHintTextView;
    @BindView(R.id.new_expense_activity_category_relative_layout_id) RelativeLayout categoryRelativeLayout;
    @BindView(R.id.new_expense_activity_category_color_image_view_id) CircleImageView categoryColorImageView;
    @BindView(R.id.new_expense_activity_category_name_text_view_id) TextView categoryNameTextView;
    @BindView(R.id.new_expense_activity_expense_date_text_view_id) TextView expenseDateTextView;
    @BindView(R.id.new_expense_activity_expense_time_text_view_id) TextView expenseTimeTextView;

    public static void newInstance(Context context) {
        Intent intent = new Intent(context, NewExpenseActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_expense_activity);
        ButterKnife.bind(this);

        setupToolbar();

        setupToolbar();
        expense = new Expense();
        progressBar.getIndeterminateDrawable().setColorFilter(ContextCompat.getColor(this, R.color.blue), PorterDuff.Mode.SRC_ATOP);
        setupCategory();
        setupDateAndTime();
        setupPhotoSourcePicker();
    }

    private void setupDateAndTime() {
        calendar = Calendar.getInstance();
        formatDateAndTime(calendar.getTime());
        expenseDateTextView.setOnClickListener(v -> {
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            DatePickerFragment datePickerFragment = DatePickerFragment.newInstance(year, month, day);
            datePickerFragment.setListener(onDateSetListener);
            datePickerFragment.show(getSupportFragmentManager(), DATE_PICKER);
        });

        expenseTimeTextView.setOnClickListener(v -> {
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            TimePickerFragment timePickerFragment = TimePickerFragment.newInstance(hour, minute);
            timePickerFragment.setListener(onTimeSetListener);
            timePickerFragment.show(getSupportFragmentManager(), TIME_PICKER);
        });
    }

    private DatePickerDialog.OnDateSetListener onDateSetListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker datePicker, int year, int monthOfYear, int dayOfMonth) {
            calendar.set(year, monthOfYear, dayOfMonth);
            formatDateAndTime(calendar.getTime());
        }
    };

    private TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            formatDateAndTime(calendar.getTime());
        }
    };

    private void formatDateAndTime(Date date) {
        // Create format
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        // Parse date and set text
        expenseDateTextView.setText(dateFormat.format(date));
        expenseTimeTextView.setText(timeFormat.format(date));
    }

    private void setupCategory() {
        loadCategory(category);

        categoryHintTextView.setOnClickListener(v -> {
            selectCategory();
        });
        categoryRelativeLayout.setOnClickListener(v -> {
            selectCategory();
        });
    }

    private void selectCategory() {
        CategoryPickerFragment categoryPickerFragment = CategoryPickerFragment.newInstance();
        categoryPickerFragment.setListener(categoryPickerListener);
        categoryPickerFragment.show(getSupportFragmentManager(), CategoryPickerFragment.class.getSimpleName());
    }

    private CategoryPickerFragment.CategoryPickerListener categoryPickerListener = new CategoryPickerFragment.CategoryPickerListener() {
        @Override
        public void onFinishExpenseCategoryDialog(Category category) {
            loadCategory(category);
        }
    };

    private void loadCategory(Category category) {
        if (category == null) {
            // Show category hint
            categoryHintTextView.setVisibility(View.VISIBLE);
            categoryRelativeLayout.setVisibility(View.INVISIBLE);
        } else {
            // Hide category hint
            categoryHintTextView.setVisibility(View.INVISIBLE);
            categoryRelativeLayout.setVisibility(View.VISIBLE);

            ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor(category.getColor()));
            categoryColorImageView.setImageDrawable(colorDrawable);
            categoryNameTextView.setText(category.getName());
        }
        // Update category
        this.category = category;
    }

    private void setupToolbar() {
        toolbar.setContentInsetsAbsolute(0,0);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        titleTextView.setText(getString(R.string.title_activity_new_expense));
        saveTextView.setVisibility(View.VISIBLE);
        backImageView.setImageResource(R.drawable.ic_window_close);
        saveTextView.setText(R.string.create);

        titleTextView.setOnClickListener(v -> close());
        backImageView.setOnClickListener(v -> close());
        saveTextView.setOnClickListener(v -> save());
    }

    private void setupPhotoSourcePicker() {
        // Photo
        photoList = new ArrayList<>();
        Bitmap cameraIconBitmap = getCameraIconBitmap();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        cameraIconBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] sampledInputData = stream.toByteArray();
        photoList.add(sampledInputData);
        expensePhotoAdapter = new ExpensePhotoAdapter(this, photoList, null);
        photoGridView.setAdapter(expensePhotoAdapter);
        photoGridView.setOnItemClickListener((AdapterView<?> adapterView, View view, int position, long l) -> {
            if (position == photoList.size() - 1) {
                checkCameraPermission();
            }
        });

        photoGridView.setOnItemLongClickListener((AdapterView<?> adapterView, View view, int position, long l) -> {
            if (position == photoList.size() - 1) {
                return false;
            }
            photoList.remove(position);
            expensePhotoAdapter.notifyDataSetChanged();
            return true;
        });
    }

    private void setPhotoSourcePicker() {
        choosePhotoSource = new AlertDialog.Builder(this);

        final ArrayAdapter<String> photoSourceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1);
        photoSourceAdapter.add(NEW_PHOTO);
        photoSourceAdapter.add(LIBRARY_PHOTO);

        choosePhotoSource.setAdapter(photoSourceAdapter, (DialogInterface dialog, int which) -> {
            String photoSource = photoSourceAdapter.getItem(which);
            if (photoSource == null) {
                //take a photo
                return;
            }

            switch (photoSource) {
                case NEW_PHOTO:
                    Log.d(TAG, "Take a photo");
                    break;
                case LIBRARY_PHOTO:
                    Log.d(TAG, "Choose photo from library");
                    break;
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE_REQUEST_CODE) {
                final boolean isCamera;
                if (data == null) {
                    isCamera = true;
                } else {
                    final String action = data.getAction();
                    isCamera = action != null && action.equals(MediaStore.ACTION_IMAGE_CAPTURE);
                }

                Uri selectedImageUri;
                if (isCamera) {
                    selectedImageUri = outputFileUri;
                } else {
                    selectedImageUri = data.getData();
                }

                try {
                    InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                    try {
                        byte[] inputData = Helpers.getBytesFromInputStream(inputStream);
                        Bitmap bitmap = Helpers.decodeSampledBitmapFromByteArray(inputData, 0, 400, 400);
                        int dimension = Helpers.getCenterCropDimensionForBitmap(bitmap);
                        bitmap = ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension);
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                        byte[] sampledInputData = stream.toByteArray();
                        byte[] cameraBytes = photoList.get(photoList.size() - 1);
                        photoList.remove(photoList.size() - 1);
                        photoList.add(sampledInputData);
                        photoList.add(cameraBytes);
                        expensePhotoAdapter.notifyDataSetChanged();
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading image byte data from uri");
                    }
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Error file with uri " + selectedImageUri + " not found", e);
                }
            }
        }
    }

    private Bitmap getCameraIconBitmap() {
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_camera_alt_white_24dp);
        Bitmap originalBitmap = ((BitmapDrawable)drawable).getBitmap();
        Paint paint = new Paint();
        ColorFilter filter = new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.gray), PorterDuff.Mode.SRC_IN);
        paint.setColorFilter(filter);
        Bitmap cameraIconBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(cameraIconBitmap);
        canvas.drawBitmap(originalBitmap, -2, 2, paint);

        return Helpers.padBitmap(cameraIconBitmap, 20, 20);
    }

    private void openImageIntent() {
        // Path to save image
        String directoryName = Helpers.dateToString(new Date(), getString(R.string.photo_date_format_string));
        final File root = new File(Environment.getExternalStorageDirectory() + File.separator + directoryName + File.separator);
        root.mkdirs();
        final File sdImageMainDirectory = new File(root, directoryName);
        outputFileUri = Uri.fromFile(sdImageMainDirectory);

        // todo: request runtime permission
        // Take a photo
        final List<Intent> cameraIntents = new ArrayList<>();
        final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> listCamera = packageManager.queryIntentActivities(captureIntent, 0);
        for (ResolveInfo res : listCamera) {
            final String packageName = res.activityInfo.packageName;
            final Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(packageName);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            cameraIntents.add(intent);
        }

        // Choose a photo
        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

        final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.photo_select_source));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[cameraIntents.size()]));
        startActivityForResult(chooserIntent, SELECT_PICTURE_REQUEST_CODE);
    }

    private void save() {
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.shared_preferences_session_key), 0);
        String loginUserId = sharedPreferences.getString(User.USER_ID, null);
        if (loginUserId == null) {
            Log.i(TAG, "Error getting login user id.");
            return;
        }
        expense.setUserId(loginUserId);

        double amount;
        try {
            amount = Double.valueOf(amountTextView.getText().toString());
            amount = Helpers.formatNumToDouble(amount);
            if (amount <= 0) {
                Toast.makeText(this, "Amount cannot be zero.", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot convert amount to double.", e);
            Toast.makeText(this, "Incorrect amount format.", Toast.LENGTH_SHORT).show();
            return;
        }
        expense.setAmount(amount);
        if (noteTextView.getText().length() > 0) {
            expense.setNote(noteTextView.getText().toString());
        } else {
            expense.setNote("");
        }
        expense.setCategoryId(category != null ? category.getId() : null);
        expense.setExpenseDate(calendar.getTime());

        ExpenseBuilder expenseBuilder = new ExpenseBuilder();
        expenseBuilder.setExpense(expense);
        photoList.remove(photoList.size() - 1);
        expenseBuilder.setPhotoList(photoList);

        progressBar.setVisibility(View.VISIBLE);
        SyncExpense.create(expenseBuilder).continueWith(onCreateSuccess, Task.UI_THREAD_EXECUTOR);
        closeSoftKeyboard();
    }

    private Continuation<JSONObject, Void> onCreateSuccess = new Continuation<JSONObject, Void>() {
        @Override
        public Void then(Task<JSONObject> task) throws Exception {
            progressBar.setVisibility(View.GONE);
            if (task.isFaulted()) {
                Log.e(TAG, "Error in creating new expense.", task.getError());
            }

            close();

            return null;
        }
    };

    public void closeSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        View view = this.getCurrentFocus();
        if (inputMethodManager != null && view != null){
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void closeWithUnSavedChangesCheck() {
        if (amountTextView.getText().length() == 0 && noteTextView.getText().length() == 0 && photoList.size() <= 1) {
            close();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_changes)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.discard, (DialogInterface dialog, int which) -> close())
                .setNegativeButton(R.string.cancel, (DialogInterface dialog, int which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void close() {
        finish();
        overridePendingTransition(0, R.anim.right_out);
    }

    @Override
    public void onBackPressed() {
        closeWithUnSavedChangesCheck();
    }

    private void checkCameraPermission() {
        PermissionsManager.verifyCameraPermissionGranted(this, (boolean isGranted) -> {
            if (isGranted) {
                openImageIntent();
            } else {
                Log.d(TAG, "Permission is not granted.");
            }
        });
    }
}
