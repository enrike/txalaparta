/*
TempoCalculator


License GPL.
by www.ixi-audio.net

Usage:
t = TempoCalculator.new(3, 1);
t.process(0) // it receives a stream of 0/1 value and will understand the change between 0 to 1 as a trigger for the tempo calculation. for instance 0,0,0,0,0,1,1,1,1,0,0,0,0,0,1,1,1,1 it will calculate the tempo acording to the first 1 of each group or 1s
t.calculate // if you deal with discrete events you can simple shut this and it will return the tempo between the present call and the previous one
*/

TempoCalculator{

	var <memorylength, <verbose=0, hit = false, bpm=0, bpms, lastTime, sanityCheck;

	*new {| memorylength = 2, verbose = 0 |
		^super.new.initTempoCalculator( memorylength, verbose );
	}

	initTempoCalculator {| memorylength, verbose |
		memorylength = memorylength;
		verbose = verbose;

		bpm = 0;
		bpms = 0.dup(memorylength);
		lastTime = 0;
	}

	sanityCheck {arg abpm;
		if (abpm == inf, {
			abpm = bpms.last;
			if (verbose > 0, {"inf".postln});
		});
		if (abpm < (bpms.last*0.6), { // hutsune. empty hit
			abpm = bpms.last;
			if (verbose > 0, {[abpm, (bpms.last*0.6), "***** gap"].postln});
		});

		^abpm;
	}

	process {arg value;
		if (value == 0, { // signal
			if (hit.not, {
				hit = true;
				bpm = this.calculate();
			});
			if (verbose > 0, {"-------------------".postln});
		}, { //silence
			hit = false;
			if (verbose > 0, {".".postln});
		});

		^bpm;
	}

	calculate {
		// there should be a timeout calculated from previous tempo values
		// to avoid getting wrong values when gaps are introduced
		var newTempo, nowTime;

		nowTime = SystemClock.seconds;
		newTempo = (60/(nowTime - lastTime)).round(0.1);
		newTempo = this.sanityCheck(newTempo);
		bpms = bpms.shift(1, newTempo); // store
		newTempo = (bpms.sum/bpms.size); // average of all stored tempos
		lastTime = nowTime;

		^newTempo;
	}

}