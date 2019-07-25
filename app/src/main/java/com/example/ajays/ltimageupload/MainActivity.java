package com.example.ajays.ltimageupload;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.okhttp.OkHttpStack;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.text.DecimalFormat;
import java.util.Random;
import java.util.UUID;

import static android.os.Environment.getExternalStorageDirectory;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1;

    private ImageView actualImageView;
    private ImageView compressedImageView;
    private TextView actualSizeTextView;
    private TextView compressedSizeTextView;
    private File actualImage;
    private File compressedImage;

    FirebaseStorage firebaseStorage;
    StorageReference storageReference;

    long tStart, tEnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        actualImageView = (ImageView) findViewById(R.id.actual_image);
        compressedImageView = (ImageView) findViewById(R.id.compressed_image);
        actualSizeTextView = (TextView) findViewById(R.id.actual_size);
        compressedSizeTextView = (TextView) findViewById(R.id.compressed_size);

        actualImageView.setBackgroundColor(getRandomColor());
        clearImage();

        firebaseStorage = FirebaseStorage.getInstance();
        storageReference = firebaseStorage.getReference();

        UploadService.NAMESPACE = BuildConfig.APPLICATION_ID;
        UploadService.HTTP_STACK = new OkHttpStack(); // a new client will be automatically created
    }

    public void chooseImage(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    public void compressImageLT(View view){
        if (actualImage == null) {
            showError("Please choose an image!");
        }else{
            tStart = System.currentTimeMillis();
            getResizedImage(MainActivity.this,actualImage);
            tEnd = System.currentTimeMillis();
            setCompressedImage();
        }
    }

    public void uploadImage(View view) throws FileNotFoundException {
        if(compressedImage != null){
            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setTitle("Uploading...");
            progressDialog.show();

            InputStream stream = new FileInputStream(compressedImage);
            StorageReference ref = storageReference.child("images/"+ UUID.randomUUID().toString());
            tStart = System.currentTimeMillis();
            ref.putStream(stream)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            progressDialog.dismiss();
                            tEnd = System.currentTimeMillis();
                            Toast.makeText(MainActivity.this, "Uploaded in " + (tEnd-tStart)/1000. + " seconds", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this, "Failed "+e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            double progress = (100.0*taskSnapshot.getBytesTransferred()/taskSnapshot
                                    .getTotalByteCount());
                            progressDialog.setMessage("Uploaded "+(int)progress+"%");
                        }
                    });
        }
    }

    public void uploadImageGotev(View view) {
        try {
            String uploadId =
                    new MultipartUploadRequest(MainActivity.this, "https://console.cloud.google.com/storage/browser/ltimageupload-245900.appspot.com")
                            .addFileToUpload(compressedImage.getAbsolutePath(), "")
                            .setNotificationConfig(new UploadNotificationConfig())
                            .setMaxRetries(2)
                            .startUpload();
        } catch (Exception exc) {
            Log.e("imageUpload", exc.getMessage(), exc);
        }
    }

    private void getResizedImage(Context context, File inputImage) {
        Integer imageMaxSize = 650;

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();

            // setting inJustDecodeBounds to true will not find bitmap but can give image dimensions first
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(String.valueOf(inputImage.getAbsolutePath()), options);
            if (options.outWidth > imageMaxSize) {
                // if image is big, we find inSampleSize and find corresponding bitmap after setting inJustDecodeBounds to false
                options.inSampleSize = options.outWidth / imageMaxSize;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeFile(String.valueOf(inputImage.getAbsolutePath()), options);
                compressedImage = resizeImage(bitmap);
            } else {
                compressedImage = inputImage;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File resizeImage(Bitmap bitmap) {
        File resizedImage = null;

        try {
            int inWidth = bitmap.getWidth();
            int inHeight = bitmap.getHeight();
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, inWidth, inHeight, false);
            resizedImage = new File(getExternalStorageDirectory().getAbsolutePath() + "/resize0.jpg");
            OutputStream fOut = new BufferedOutputStream(new FileOutputStream(resizedImage));
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, fOut);
            fOut.flush();
            fOut.close();
            bitmap.recycle();
            scaledBitmap.recycle();
            // cannot delete resized file, need to get the file while uploading
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resizedImage;
    }

    private void setCompressedImage() {
        compressedImageView.setImageBitmap(BitmapFactory.decodeFile(compressedImage.getAbsolutePath()));
        compressedSizeTextView.setText(String.format("Size : %s", getReadableFileSize(compressedImage.length())));
        tEnd = System.currentTimeMillis();

        Toast.makeText(this, "Compressed image save in " + compressedImage.getAbsolutePath(), Toast.LENGTH_LONG).show();
        Log.d("Compressor", "Compressed image save in " + compressedImage.getAbsolutePath());
        Log.d("Compressor","Time Elapsed: " + (tEnd-tStart));
        Toast.makeText(this, "Time Elapsed: " + (tEnd-tStart), Toast.LENGTH_LONG).show();
    }

    private void clearImage() {
        actualImageView.setBackgroundColor(getRandomColor());
        compressedImageView.setImageDrawable(null);
        compressedImageView.setBackgroundColor(getRandomColor());
        compressedSizeTextView.setText("Size : -");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (data == null) {
                showError("Failed to open picture!");
                return;
            }
            try {
                actualImage = FileUtil.from(this, data.getData());
                actualImageView.setImageBitmap(BitmapFactory.decodeFile(actualImage.getAbsolutePath()));
                actualSizeTextView.setText(String.format("Size : %s", getReadableFileSize(actualImage.length())));
                clearImage();
            } catch (IOException e) {
                showError("Failed to read picture data!");
                e.printStackTrace();
            }
        }
    }

    public void showError(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    private int getRandomColor() {
        Random rand = new Random();
        return Color.argb(100, rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
    }

    public String getReadableFileSize(long size) {
        if (size <= 0) {
            return "0";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
