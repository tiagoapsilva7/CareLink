package com.example.carelink.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.carelink.model.UserModel;


import java.util.regex.Matcher;
import java.util.regex.Pattern;


//Useful functions used in multiple activities
/**
 * Helpers shared across activities: passing a
 * {@link com.example.carelink.model.UserModel} through an Intent's extras,
 * loading a profile picture into an ImageView via Glide, and the small
 * text-validation routines used by the chat and form screens (integer checks,
 * link detection, and the media-term lookup that decides how a shared URL is
 * described).
 */
public class AndroidUtil {



    //important for the chat:
    public static void passUserModelAsIntent(Intent intent, UserModel model){
        intent.putExtra("username",model.getUsername());
        intent.putExtra("email",model.getEmail());
        intent.putExtra("userId", model.getUserId());
        intent.putExtra("fcmToken", model.getFcmToken());

    }

    //important for the chat:
    public static UserModel getUserModelFromIntent(Intent intent){
        UserModel userModel = new UserModel();
        userModel.setUsername(intent.getStringExtra("username"));
        userModel.setEmail(intent.getStringExtra("email"));
        userModel.setUserId(intent.getStringExtra("userId"));
        userModel.setFcmToken(intent.getStringExtra("fcmToken"));
        return userModel;
    }

    //Setting profile pics
    public static void setProfilePic(Context context, Uri imageUri, ImageView imageView){
        Glide.with(context).load(imageUri).apply(RequestOptions.circleCropTransform()).into(imageView);
    }


    //verification in RegisterActivity
    public static boolean isInteger(String input) {
        try {
            Integer.parseInt(input); // Tenta converter a String para inteiro
            return true; // Sucesso significa que é um número inteiro
        } catch (NumberFormatException e) {
            return false; // Falha na conversão significa que não é
        }
    }

    public static boolean isLink(String text) {
        String urlPattern = "^(https?|ftp)://.*$"; //verification of the url
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(text);
        return matcher.matches();
    }

    // Verifica se o link contém os termos "audio", "video" ou "image"
    public static String containsMediaTerm(String text) { // looks for the key terms in the url

        if (text.contains("audio")){
            return "audio";
        }
        else if (text.contains ("image")){
            return "image";
        }
        else if(text.contains("video")){
            return "video";
        }

        else{
            return null;
        }


    }

    public static String grammar(String term){
        if (term == "audio" || term == "image"){
            return "n ";
        }
        else {
            return " ";
        }
    }
}

