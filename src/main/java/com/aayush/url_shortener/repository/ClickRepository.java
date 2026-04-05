package com.aayush.url_shortener.repository;

import com.aayush.url_shortener.model.Click;
import com.aayush.url_shortener.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClickRepository extends JpaRepository<Click, String> {

    List<Click> findByUrl(Url url);

    long countByUrl(Url url);
}