/*
TempoCalculator


License GPL.
by www.ixi-audio.net

Usage:
t = TempoCalculator.new(3, 1);
t.process(0) // it receives a stream of 0/1 value and will understand the change between 0 to 1 as a trigger for the tempo calculation (being 0 signal and 1 silence).
t.calculate // if you deal with discrete events you can simple shut this and it will return the tempo between the present call and the previous one
*/

TempoCalculator{

	var <memorylength, >verbose=0, hit = false, bpm=0, bpms, lastTime, sanityCheck, parent;

	*new {| parent=nil, memorylength = 2, verbose = 0 |
		^super.new.initTempoCalculator( parent, memorylength, verbose );
	}

	initTempoCalculator {| aparent, amemorylength, averbose |
		parent = aparent;
		memorylength = amemorylength;
		verbose = averbose;

		bpm = 0;
		bpms = 0.dup(amemorylength);
		lastTime = 0;
	}

	sanityCheck {arg abpm;
		if (abpm == inf, {
			abpm = bpms.last;
			if (verbose > 0, {"inf".postln});
		});
		if (abpm < (bpms.last*0.6), { // hutsune. empty hit
			abpm = bpms.last;
			if (verbose > 1, {[abpm, (bpms.last*0.6), "***** gap"].postln});
		});

		^abpm;
	}

	process {arg value;
		if (value == 0, { // signal
			if (hit.not, { // new hit arrived
				hit = true;
				bpm = this.calculate();
				parent.newhit();
			});
			if (verbose > 2, {"-------------------".postln});
		}, { //silence
			hit = false;
			if (verbose > 2, {".".postln});
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