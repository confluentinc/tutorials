package io.confluent.developer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicInterestJoinerTest {

   @Test
   void joinTest() {

        MusicInterest returnedMusicInterest;

        Album theAlbum = new Album("100", "Album Title", "testing", "the artist");
        TrackPurchase theTrackPurchase = new TrackPurchase("5000", "song-title","100", 1.25);
        MusicInterest expectedMusicInterest = new MusicInterest("100-5000", "testing", "the artist");

        MusicInterestJoiner joiner = new MusicInterestJoiner();
        returnedMusicInterest = joiner.apply(theTrackPurchase, theAlbum);

        assertEquals(returnedMusicInterest, expectedMusicInterest);
    }
}