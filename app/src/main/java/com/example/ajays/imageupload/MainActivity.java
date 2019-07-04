package com.example.ajays.imageupload;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Random;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1;

    private ImageView actualImageView;
    private ImageView compressedImageView;
    private TextView actualSizeTextView;
    private TextView compressedSizeTextView;
    private File actualImage;
    private File compressedImage;

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

    public void uploadImage(View view){
        BlobId blobId = BlobId.of("lt-images", "test1");
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/jpeg").build();
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeFile(compressedImage.getAbsolutePath(),options);
        bitmap = Bitmap.createScaledBitmap(bitmap,bitmap.getWidth(),bitmap.getHeight(),true);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);

        try {
            Storage storage = StorageOptions.newBuilder()
                    .setCredentials(ServiceAccountCredentials
                            .fromStream(new FileInputStream("@app/gcs-api-key.json")))
                    .build()
                    .getService();
            Toast.makeText(MainActivity.this, "Hello", Toast.LENGTH_SHORT).show();
            storage.create(blobInfo,stream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("ImageUpload", "ioexception", e);
            Toast.makeText(MainActivity.this, "Sorry boss", Toast.LENGTH_SHORT).show();
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
            resizedImage = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).
                    getAbsolutePath() + "/resize" + 0 + ".jpg");
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
