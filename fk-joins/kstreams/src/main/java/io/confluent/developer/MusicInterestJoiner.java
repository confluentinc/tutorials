package io.confluent.developer;

import org.apache.kafka.streams.kstream.ValueJoiner;

public class MusicInterestJoiner implements ValueJoiner<TrackPurchase, Album, MusicInterest> {
    public MusicInterest apply(TrackPurchase trackPurchase, Album album) {
        return new MusicInterest(album.id() + "-" + trackPurchase.id(),
                album.genre(),
                album.artist());
    }
}