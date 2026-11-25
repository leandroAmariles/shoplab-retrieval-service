package com.shoplab.retrieval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The knowledge base of ShopLab: documents, their vectors and the search over them (specs/LAB-129). */
@SpringBootApplication
public class RetrievalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetrievalServiceApplication.class, args);
    }
}
