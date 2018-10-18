package org.genecash.garagedoor;

// this was a major pain in the ass to figure out
//
// you can't override Activity in a class, then have 2 classes call it, because Android throws a shit-fit when the called overriding class
// is not  in the manifest
//
// so instead of Class A, then application Class B does A with X and application Class B does A with Y,
// you have to have application Class A with X, then application Class B does A with Y.
public class GarageDoorButton extends GarageDoorOpen {
    public GarageDoorButton() {
        command = "TOGGLE";
        logname = "button";
    }
}
