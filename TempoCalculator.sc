/*
TempoCalculator


License GPL.
by www.ixi-audio.net

Usage:
t = TempoCalculator.new(3, 1);
t.calculate // returns the BPMs from last time calculate() was called
t.lasttime // returns the time when a calculate() was called last time
*/

TempoCalculator{

	var memorylength, bpms, bpm, <lasttime;

	*new {| memorylength = 2 |
		^super.new.initTempoCalculator( memorylength );
	}

	initTempoCalculator {| amemorylength |
		memorylength = amemorylength;
		this.reset();
	}

	reset {
		bpms = 0.dup(memorylength);
		lasttime = 0;
	}

	pushlasttime { // push to next compass
		lasttime = lasttime + (60/bpm);
	}

	sanityCheck {arg abpm;
		if (abpm == inf, {
			abpm = bpms.last;
			"inf".postln;
		});
		if (abpm > 250, { abpm = bpms.last}); // if too high something went wrong

		^abpm;
	}

	calculate {
		var newTempo, nowTime;

		nowTime = Main.elapsedTime;// SystemClock.seconds;
		newTempo = (60/(nowTime - lasttime)).round(0.1);
		newTempo = this.sanityCheck(newTempo);
		bpms = bpms.shift(1, newTempo); // store
		newTempo = (bpms.sum/bpms.size); // average of all stored tempos
		bpm = newTempo;
		lasttime = nowTime;

		^newTempo;
	}
}