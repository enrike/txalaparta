// license GPL
// by www.ixi-audio.net

/*
t = TxalaOnsetDetection.new(nil, s)
to do: improve detected amplitude range and values
*/

TxalaOnsetDetection{

	var server, parent, curPattern, <synth, synthOSCcb, >processflag, <patternsttime, sttime, freqtable;

	*new {| aparent=nil, aserver, afreqtable |
		^super.new.initTxalaOnsetDetection(aparent, aserver, afreqtable);
	}

	initTxalaOnsetDetection { arg aparent, aserver, afreqtable;
		parent = aparent;
		server = aserver;
		freqtable = afreqtable;
		this.reset()
	}

	reset {
		processflag = false;
		patternsttime = 0;
		curPattern = nil;
		sttime = Main.elapsedTime;
		this.doAudio();
	}

	kill {
		synth.free;
		synth = nil;
		OSCdef(\txalaonsetOSCdef).clear;
		OSCdef(\txalaonsetOSCdef).free;
	}

	matchfreq { arg rawfreq;
		// here we need to match the detected freq to one of the current freqs in the table freqtable
		// maybe it would be better to pass a table with the buffers of the sounds so that I can here
		// analyse them and extract its freq characteristics and create the freqtable dynamically here.
		^rawfreq;
	}

	doAudio {
		this.kill(); // force

		SynthDef(\txalaonsetlistener, { |in=0, amp=1, threshold=0.6, relaxtime=2.1, floor=0.1, mingap=0.1|
		 	var fft, onset, signal, level=0, freq=0, hasFreq=false;
		 	signal = SoundIn.ar(in) * amp;
		 	fft = FFT(LocalBuf(2048), signal);
		 	onset = Onsets.kr(fft, threshold, \rcomplex, relaxtime, floor, mingap, 11, 1, 0);// beat detection
		 	/*	*kr (chain, threshold: 0.5, odftype: 'rcomplex', relaxtime: 1, floor: 0.1, mingap: 10, medianspan: 11, whtype: 1, rawodf: 0)*/
		 	level = Amplitude.kr(signal);
		 	# freq, hasFreq = Tartini.kr(signal,  threshold: 0.93, n: 2048, k: 0, overlap: 1024, smallCutoff: 0.5 );
		 	SendReply.kr(onset, '/txalaonset', [level, hasFreq, freq]);
		 }).add;

		synth = Synth(\txalaonsetlistener, [
			\in, ~listenparemeters.in,
			\amp, ~listenparemeters.amp,
			\threshold, ~listenparemeters.onset.threshold,
			\relaxtime, ~listenparemeters.onset.relaxtime,
			\floor, ~listenparemeters.onset.floor,
			\mingap, ~listenparemeters.onset.mingap
		]);

		OSCdef(\txalaonsetOSCdef, {|msg, time, addr, recvPort| this.process(msg)}, '/txalaonset', server.addr);
	}

	process { arg msg;
		var hitdata, hittime, freq=0;

		if (processflag.not, {
			if (curPattern.isNil, { // this is the first hit of a new pattern
				hittime = 0; // start counting on first one
				patternsttime = Main.elapsedTime;
				if (parent.isNil.not, { parent.newgroup() });
				("~~~~~~~~~~~~~~~~~~~~~~~~~~~~ new group" + (patternsttime-sttime) + patternsttime).postln;
			},{
				hittime = Main.elapsedTime - patternsttime; // distance from first hit of this group
			});

			if ( msg[4].asBoolean, { freq = this.matchfreq(msg[5]) });


			hitdata = ().add(\time -> hittime)
			            .add(\amp -> msg[3])
			            .add(\player -> 1) //always 1 in this case
			            .add(\plank -> freq);
			curPattern = curPattern.add(hitdata);
			("++++++++++++++++++++++++++++++++++++++++++++++++++++" + curPattern.size + msg[3] + msg[4] + msg[5]).postln;
			if (parent.isNil.not, { parent.newonset(Main.elapsedTime, msg[3], 1, freq) });
		});

	}

	closegroup { // group ended detected by silence detector. must return info about the pattern played.
		var pat = curPattern;
		curPattern = nil;
		^pat;
	}
}