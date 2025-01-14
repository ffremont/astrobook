package com.github.ffremont.astack.web;

import com.github.ffremont.astack.dao.DeepSkyCatalogDAO;
import com.github.ffremont.astack.dao.PictureDAO;
import com.github.ffremont.astack.service.model.ConstellationData;
import com.github.ffremont.astack.service.model.NovaStatus;
import com.github.ffremont.astack.service.model.Picture;
import com.github.ffremont.astack.service.model.PictureState;
import com.github.ffremont.astack.web.model.Shot;
import com.github.ffremont.astack.web.model.WebTag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/pictures")
@AllArgsConstructor
public class PictureController {
    private final PictureDAO pictureDAO;
    private DeepSkyCatalogDAO deepSkyCatalogDAO;

    @GetMapping("/tags")
    public List<WebTag> tags() {
        return pictureDAO.getAll().flatMap(picture -> {
            List<WebTag> tags = new ArrayList<>(picture.getTags().stream().map(t -> new WebTag(t, t)).toList());
            tags.add(new WebTag(picture.getLocation(), picture.getLocation()));
            tags.add(new WebTag(picture.getWeather().label()+" météo", picture.getWeather().name()));
            tags.add(new WebTag(picture.getMoonPhase().label(), picture.getMoonPhase().name()));
            tags.add(
                    new WebTag(deepSkyCatalogDAO
                            .getConstellationByAbr(picture.getConstellation())
                            .map(ConstellationData::label)
                            .orElse(picture.getConstellation()),
                            picture.getConstellation()));
            return tags.stream().filter(Objects::nonNull);
        }).distinct().toList();
    }

    @GetMapping
    public List<Picture> all() {
        var pictures = pictureDAO.getAll().map(picture -> {
            ArrayList<String> tags = new ArrayList<>(picture.getTags());
            tags.add(picture.getLocation());
            tags.add(picture.getWeather().name());
            tags.add(picture.getMoonPhase().name());
            tags.add(picture.getConstellation());
            return picture.toBuilder().webTags(tags.stream().filter(Objects::nonNull).toList()).build();
        }).sorted(Collections.reverseOrder()).toList();

        return pictures;
    }

    @GetMapping("/status")
    public NovaStatus status(@RequestParam("id") List<String> ids) {
        var areDone = ids.stream().allMatch(id ->
                PictureState.DONE.equals(Optional.ofNullable(pictureDAO.getById(id))
                        .orElse(Picture.builder().id(id).state(PictureState.DONE).build())
                        .getState())
        );
        return new NovaStatus(areDone ? PictureState.DONE.name() : PictureState.PENDING.name());
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable("id") String id) {
        pictureDAO.remove(id);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable("id") String id, @RequestBody Picture picture) {
        var originalPicture = pictureDAO.getById(id);

        pictureDAO.refresh(originalPicture.toBuilder()
                        .name(picture.getName())
                        .type(picture.getType())
                        .dateObs(picture.getDateObs())
                        .constellation(picture.getConstellation())
                        .camera(picture.getCamera())
                        .instrument(picture.getInstrument())
                        .tags(picture.getTags())
                        .corrRed(picture.getCorrRed())
                        .location(picture.getLocation())
                        .moonPhase(picture.getMoonPhase())
                        .gain(picture.getGain())
                        .exposure(picture.getExposure())
                        .stackCnt(picture.getStackCnt())
                .build());
    }


    @GetMapping("/images/{id}")
    public ResponseEntity image(@PathVariable("id") String id) {
        return ResponseEntity.status(200).contentType(MediaType.IMAGE_JPEG).body(pictureDAO.getBin(id, PictureDAO.PICTURE_FILENAME));
    }

    @GetMapping("/raws/{id}")
    public ResponseEntity raw(@PathVariable("id") String id) {
        return ResponseEntity.status(200)
                .header("Content-Type", "image/fits").body(pictureDAO.getBin(id, PictureDAO.RAW_FILENAME));
    }

    @GetMapping("/thumbs/{id}")
    public ResponseEntity thumb(@PathVariable("id") String id) {
        return ResponseEntity.status(200)
                .contentType(MediaType.IMAGE_JPEG).body(pictureDAO.getBin(id, PictureDAO.THUMB_FILENAME));
    }

    @GetMapping("/annotated/{id}")
    public ResponseEntity annotated(@PathVariable("id") String id) {
        return ResponseEntity.status(200)
                .contentType(MediaType.IMAGE_JPEG).body(pictureDAO.getBin(id, PictureDAO.ANNOTATED_FILENAME));
    }
}
