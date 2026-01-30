package main

type TempReading struct {
	DeviceID    string  `avro:"deviceId"`
	Temperature float32 `avro:"temperature"`
}
