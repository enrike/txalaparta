// license GPL
// by www.ixi-audio.net

/*
t = TxalaOnsetDetection.new(nil, s)
to do: improve detected amplitude range and values, plank detection
*/

TxalaOnsetDetection{

	var server, parent, <curPattern, <synth, synthOSCcb, >processflag, <patternsttime, sttime;
	var features;

	*new {| aparent=nil, aserver |
		^super.new.initTxalaOnsetDetection(aparent, aserver);
	}

	initTxalaOnsetDetection { arg aparent, aserver;
		parent = aparent;
		server = aserver;
		features = [\chroma];//, \freq, \keyt]; // can combine different analysis techniques
		this.reset()
	}

	reset {
		processflag = false;
		patternsttime = 0;
		curPattern = nil;
		sttime = SystemClock.seconds;
		this.doAudio();
	}

	kill {
		synth.free;
		synth = nil;
		OSCdef(\txalaonsetOSCdef).clear;
		OSCdef(\txalaonsetOSCdef).free;
	}

	closegroup { // group ended detected by silence detector. must return info about the pattern played and clear local data.
		var pat = curPattern;
		if ( curPattern.isNil, {
			"curPattern is NIL!!".postln
		}, {
			if ( pat.size > 4, { pat = pat[..3] });// discard if longer than 4. not sure if this can be skipped
		});

		curPattern = nil;
		^pat;
	}

	doAudio {
		this.kill(); // force
		/*
		here the problem is that in the one hand we need to know the time of the onset asap but on the other hand we cannot get
		meaningful data from the sound until 0.04 millisecs are gone because of the chaotic nature of the sound in the start area
		*/
		SynthDef(\txalaonsetlistener, { |in=0, gain=1, threshold=0.6, relaxtime=2.1, floor=0.1, mingap=1, offset=0.04, comp_thres=0.3|
		 	var fft, fft2, onset, chroma, keyt, signal, level=0, freq=0, hasfreq=false, del;
		 	signal = SoundIn.ar(in)*gain;
			level = WAmp.kr(signal, offset);
			signal = Compander.ar(signal, signal, // expand loud sounds and get rid of low ones
				thresh: comp_thres,// THIS IS CRUCIAL. in RMS
				slopeBelow: 1.9, // almost noise gate
				slopeAbove: 1.1, // >1 to get expansion
				clampTime: 0.005,
				relaxTime: 0.01
			);
		 	fft = FFT(LocalBuf(2048), signal, wintype:1);
			fft2 = FFT(LocalBuf(2048), HPF.ar(signal, 100), wintype:1); // get rid of low freqs for chromagram
			chroma = Chromagram.kr(fft2, 2048,
				n: 12,
				tuningbase: 32.703195662575,
				octaves: 8, // tried higher 12, 24... but got worst results
				//integrationflag: 1, // looks to work better if this is on
				coeff: 0.9,
				octaveratio: 2,
				perframenormalize: 1
			);
		 	onset = Onsets.kr(fft, threshold, \rcomplex, relaxtime, floor, mingap, medianspan:11, whtype:1, rawodf:0);

			del = DelayN.kr(onset, offset, offset); // CRUCIAL. percussive sounds are too chaotic at the beggining

			SendReply.kr(del, '/txalaonset', (chroma++[level]));
		 }).add;

		{
			synth = Synth(\txalaonsetlistener, [
				\in, ~listenparemeters.in,
				\gain, ~listenparemeters.gain,
				\threshold, ~listenparemeters.onset.threshold,
				\relaxtime, ~listenparemeters.onset.relaxtime,
				\floor, ~listenparemeters.onset.floor,
				\mingap, ~listenparemeters.onset.mingap,
				\comp_thres, ~listenparemeters.tempo.comp_thres,
			]);
		}.defer(0.5);

		OSCdef(\txalaonsetOSCdef, {|msg, time, addr, recvPort| this.process(msg)}, '/txalaonset', server.addr);
	}

	process { arg msg;
		var hitdata, hittime, localtime, plank=0, chroma, level, off, data = ();

		localtime = SystemClock.seconds;

		msg = msg[3..]; // remove OSC data
		chroma  = msg[0..11]; //chroma 12 items
		level   = msg[12];

		if (curPattern.isNil, { // this is the first hit of a new pattern
			hittime = 0; // start counting on first one
			patternsttime = localtime; //  abs start time of the new group
			//parent.broadcastgroupstarted(); // needed by onset detector to close pattern groups. should this be called from here or from onset detection??
		},{
			hittime = localtime - patternsttime; // distance from first hit of this group
		});

		data.add(\chroma -> chroma); // 12 items

		if (~recindex.isNil, { // plank analysis
			plank = this.matchplank(data);
		},{
			["storing hit chroma  data", ~recindex].postln;
			~plankdata[~recindex] = data;
			{ parent.chromabuttons[~recindex].valueAction = 0 }.defer; // off rec button
		});

		hitdata = ().add(\time   -> hittime)
		.add(\amp    -> level)
		.add(\player -> 1) // here always 1 for errena player
		.add(\plank  -> plank);
		curPattern = curPattern.add(hitdata);

		parent.newonset( localtime, level, 1, plank );
	}

	matchplank {arg data;
		var fdata, plank, res = Array.new(~plankdata.size);
		fdata = data.atAll(features).flat; //filtered data

		~plankdata.do({ arg dataset;
			var fdataset;
			if (dataset.size.asBoolean, {
				fdataset = dataset.atAll(features).flat;
				res = res.add( (fdata-fdataset).abs.sum );
			});
		});

		plank = res.minIndex;
		if (plank.isNil, { plank = 0 }); // there was no match
		^plank
	}
}