
TxalaScore {
	
	var win, view, events, selected, timeoffset, image, record, recordtask;
	var timeframe = 12;
	var imageArray;
	
	*new {|parent, rect, numPlanks=3|
		^super.new.initTxalaScore( parent, rect, numPlanks );
	}

	initTxalaScore {|parent, rect, numPlanks|
		var plankheight;
		selected = nil;
		win = parent;
		imageArray = [];
		view = UserView.new(parent, rect);
		view.background = Color.white;
		plankheight = (view.bounds.height/(numPlanks+1));		view.drawFunc_({
			// the planks
			(numPlanks).do({arg i;
				Pen.line(Point(0, plankheight*(i+1)), Point(view.bounds.width,plankheight*(i+1))); 
			});
			Pen.stroke;
			// the time grid (vertical lines)
			Pen.color = Color.black.alpha_(0.2);
			20.do({arg i;
				Pen.line(Point((view.bounds.width/20)*i, 0), Point((view.bounds.width/20)*i, view.bounds.height)); 
			});
			Pen.stroke;
			// the events themselves
			Pen.color = Color.black;
			events.do({arg event;
			//	var time = (event.time-timeoffset-speed) * (view.bounds.width/speed);
				var time = (event.time-timeoffset) * (view.bounds.width/timeframe);
				Pen.color = if(event.player == 1, {Color.red.alpha_(0.5)}, {Color.blue.alpha_(0.5)});
				Pen.fillRect(Rect(time-4, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-4)).abs, 8, 8)); 
				Pen.color = Color.black;
				Pen.line(Point(time, (view.bounds.height-(plankheight*event.plank)).abs), Point(time, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-8)).abs)); 
				Pen.addRect(Rect(time-4, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-4)).abs, 8, 8)); 
				Pen.stroke;
			});
		});
		view.mouseDownAction_({|view, x, y, mod|
			selected = nil;
			block{arg break;
				events.do({ arg event, i;
					var rect = Rect(event.time * view.bounds.width-4, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-4)).abs, 8, 8);
					if(rect.contains(Point(x,y)), { 
						 "Inside : ".post; i.postln;
						selected = i; 
						break.value(); 
					});
				});
			};
			if(mod == 262401, {
				events = events.add(().add(\time -> (x/view.bounds.width)).add(\amp -> (y/view.bounds.height)));
				this.update(events);
				view.update;
			});
		});
		view.mouseMoveAction_({|view, x, y|
			if(selected.isNil.not, {
				events[selected].time = x/view.bounds.width;
				events[selected].amp = ((view.bounds.height-y).abs).linlin( events[selected].plank*plankheight , (events[selected].plank+1)*plankheight, 0, 1).postln;
				this.update(events);
				view.update;
			});
			
		});
		view.mouseUpAction_({ this.sortEvents });
		view.keyDownAction_({|view, key, sm, wh|
			[view, key, sm, wh].postln;
			if(wh == 127, {
				events.removeAt(selected);	
				this.update(events);
				view.update;
			});
		});
		
	}

	// start the scrolling movement (better not use this and do it from the outside - see example)
	scrolling_ {arg bool;
		var task; // this needs to be declared above, if used;
		var offsettime = Main.elapsedTime;		
		task = fork{
			inf.do({arg i;
				var now = Main.elapsedTime - offsettime;
				{this.update(events, now)}.defer;
				0.05.wait;
			});
		};
	}
	
	// is the score being recorded? (images saved to disk)
	recordScore_ {arg bool;
		record = bool;
		if(record, { // if starting recording
			recordtask = fork{
				inf.do({arg i;
					{this.grabScoreIntoImage(i)}.defer;
					timeframe.wait;
				});
			};
		}, { // if stopping
			var wwin, scrollview, userview;
			wwin = Window.new("score", win.bounds).front;
			scrollview = SCScrollView(wwin, Rect(0,0, win.bounds.width, win.bounds.height) );
			// now how to draw all the images into a Userview? Using imageArray? or images from disk? Python?
			userview = UserView.new(scrollview, Rect(0,0, scrollview.bounds.width*3, scrollview.bounds.height));
			userview.background_(Color.green);
			userview.drawFunc_({
				imageArray.do({arg image, i;
					image.drawAtPoint(Point(image.width*i, 0), Rect(0,0,2780, 800));
				//	Pen.imageAtPoint(image, Point(image.width*i, 0));
					userview.refresh;

				});
			});
		});
		
	}
	// the time it takes to scroll from right to left
	timeframe_{arg timef;
		timeframe = timef;
	}
	
	sortEvents {
		events = events.sort({arg e1, e2; e1.time <= e2.time });
	}
	
	update { |arr, timeoff=0|
		timeoffset = timeoff-timeframe;
		events = arr;
		view.refresh;
	}
	
	grabScoreIntoImage {arg number;
		var tempimg;
		tempimg = Image.fromWindow(win, view.bounds);
		imageArray = imageArray.add(tempimg);
		tempimg.write("~/Desktop/txalascores/txalascore_"++number.asString++".png");
	}
	
	postEvents {
		" EVENTS ____________________ \n".postln;
		events.postln;	
	}
	
	

}

/*

e = {arg i; ().add(\time -> (0.1+ (i/10))).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1))}!8;
w = Window.new("txalascore", Rect(100, 100, 800, 500)).front;
x = TxalaScore.new(w, Rect(10, 10, 600, 400));
x.timeframe =2;

x.update(e, 1);
x.postEvents;


e = {arg i; ().add(\time -> (0.1+ (i/10))).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1))}!8;
o = e.reject({arg event; event.player == 1})
t = e.reject({arg event; event.player == 0})

fork{
	20.do({
		e = {arg i; ().add(\time -> (0.1+ (i/10))).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1))}!8;
{x.update(e)}.defer;

		2.wait;
		})
	}

o = e.reject({arg event; event.player == 1})
t = e.reject({arg event; event.player == 0})

	
// ////////////////////

// example 1
(
w = Window.new("txalascore", Rect(100, 100, 1400, 500)).front;
x = TxalaScore.new(w, Rect(10, 10, 1400, 400), 3);

e = [];
t = Main.elapsedTime;

fork{
	inf.do({arg i;
		var newtime = Main.elapsedTime - t;
		e = e.add(().add(\time -> newtime).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1)));
		0.1.wait;
	});
};


fork{
	inf.do({arg i;
		var now = Main.elapsedTime - t;
		{x.update(e, now)}.defer;
		0.05.wait;
		})
	};

x.timeframe =6;

)

// example 2

b = Buffer.alloc(s, 512);

SynthDef(\onset, {arg buffer=0;
	var chain, onset, pitch, hasFreq, in, signal;
	in = SoundIn.ar(0);
	chain = FFT(buffer, in);
	onset = Onsets.kr(chain, 0.9, \rcomplex);
	#pitch, hasFreq = Tartini.kr(in);
	SendReply.kr(onset, '/onset', 1, pitch);
	//SendTrig.kr(onset, 1);
	//signal = WhiteNoise.ar(EnvGen.kr(Env.perc(0.001, 0.1, 0.2), onset));
	//Out.ar(1, signal);
}).store;

(
w = Window.new("txalascore", Rect(100, 100, 1400, 500)).front;
x = TxalaScore.new(w, Rect(10, 10, 1400, 400), 3);

e = [];
t = Main.elapsedTime;

Synth(\onset, [\buffer, b]);
t = Main.elapsedTime;

o = OSCdef(\listener, { arg msg, time;
	[time, msg].postln;
	e = e.add(().add(\time -> (time-t)).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1)));
},'/onset', s.addr);


fork{
	inf.do({arg i;
		var now = Main.elapsedTime - t;
		{x.update(e, now)}.defer;
		0.05.wait;
		})
	};

x.timeframe =22;

)
x.grabScoreIntoImage








///////// example 3 (Using x.recordScore = true, which stores images of the score to disk)

b = Buffer.alloc(s, 512);

(
w = Window.new("txalascore", Rect(100, 100, 1400, 500)).front;
x = TxalaScore.new(w, Rect(10, 10, 1400, 400), 3);

e = [];

Synth(\onset, [\buffer, b]);
t = Main.elapsedTime;

o = OSCdef(\listener, { arg msg, time;
	[time, msg].postln;
	e = e.add(().add(\time -> (time-t)).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1)));
},'/onset', s.addr);



fork{
	inf.do({arg i;
		var now = Main.elapsedTime - t;
		{x.update(e, now)}.defer;
		0.05.wait;
		})
	};

x.timeframe =6;
x.recordScore = true;

)
x.grabScoreIntoImage










///////// example 4 (Using x.recordScore = true, which stores images of the score to disk) but not onsets

b = Buffer.alloc(s, 512);

(
w = Window.new("txalascore", Rect(100, 100, 1400, 500)).front;
x = TxalaScore.new(w, Rect(10, 10, 1400, 400), 3);
t = Main.elapsedTime;

e = [];

fork{
	inf.do({arg i;
		var newtime = Main.elapsedTime - t;
		e = e.add(().add(\time -> newtime).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1)));
		0.1.wait;
	});
};


fork{
	inf.do({arg i;
		var now = Main.elapsedTime - t;
		{x.update(e, now)}.defer;
		0.05.wait;
		})
	};

x.timeframe =6;
x.recordScore = true;

)
x.grabScoreIntoImage

	
*/

