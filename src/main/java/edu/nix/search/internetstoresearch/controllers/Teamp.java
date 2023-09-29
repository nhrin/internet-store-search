package edu.nix.search.internetstoresearch.controllers;

import java.util.Arrays;

public class Teamp {
    public static void main(String[] args) {
//        String regexp = "[^a-zA-Z0-9]";

        String regexp = "[^\\p{L}\\d]";



        String q = "paxlovid: ывыв [(lol kek)]";

        q = q.replaceAll(regexp, " ");
        System.out.println(q);

//        String[] split = q.split(regexp);
//        System.out.println(Arrays.toString(split));


        String nullStr = null;

        try {
            System.out.println(nullStr.length());
        } catch (Exception e) {
            System.out.println(e.getStackTrace());
        }


    }
}
