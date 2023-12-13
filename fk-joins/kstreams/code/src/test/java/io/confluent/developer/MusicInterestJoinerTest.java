package io.confluent.developer;

import io.confluent.developer.avro.Album;
import io.confluent.developer.avro.MusicInterest;
import io.confluent.developer.avro.TrackPurchase;
import org.junit.Test;

import static org.junit.Assert.*;

public class MusicInterestJoinerTest {

    @Test
    public void apply() {

        MusicInterest returnedMusicInterest;

        Album theAlbum = Album.newBuilder().setTitle("Album Title").setId(100).setArtist("the artist").setGenre("testing").build();
        TrackPurchase theTrackPurchase = TrackPurchase.newBuilder().setId(5000).setAlbumId(100).setPrice(1.25).setSongTitle("song-title").build();
        MusicInterest expectedMusicInterest = MusicInterest.newBuilder().setArtist("the artist").setId("100-5000").setGenre("testing").build();

        MusicInterestJoiner joiner = new MusicInterestJoiner();
        returnedMusicInterest = joiner.apply(theTrackPurchase, theAlbum);

        assertEquals(returnedMusicInterest, expectedMusicInterest);
    }
}