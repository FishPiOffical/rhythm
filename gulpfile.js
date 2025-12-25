/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * @file frontend tool.
 *
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.8.0.2, Mar 17, 2019
 */

'use strict'

var gulp = require('gulp')
var concat = require('gulp-concat')
var cleanCSS = require('gulp-clean-css')
var terser = require('gulp-terser')
var sass = require('gulp-sass')(require('sass'));
var rename = require('gulp-rename')
var del = require('del')

function sassProcess () {
  return gulp.src('./src/main/resources/scss/*.scss').
    pipe(sass({outputStyle: 'compressed', includePaths: ['node_modules']}).
      on('error', sass.logError)).
    pipe(gulp.dest('./src/main/resources/css'))
}

function themeSassProcess () {
  return gulp.src('./src/main/resources/scss/theme/*.scss').
    pipe(sass({outputStyle: 'compressed', includePaths: ['node_modules']}).
      on('error', sass.logError)).
    pipe(gulp.dest('./src/main/resources/css/theme'))
}


function sassProcessWatch () {
  gulp.watch('./src/main/resources/scss/*.scss', sassProcess)
  gulp.watch('./src/main/resources/scss/theme/*.scss', themeSassProcess)
}



gulp.task('watch', gulp.series(sassProcessWatch))

function cleanProcess () {
  return del(['./src/main/resources/js/*.min.js'])
}

function minArticleCSS () {
  // min article css
  return gulp.src([
    './src/main/resources/js/lib/diff2html/diff2html.min.css']).
    pipe(cleanCSS()).
    pipe(concat('article.min.css')).
    pipe(gulp.dest('./src/main/resources/js/lib/compress/'))
}

function minJS () {
  // min js
  return gulp.src('./src/main/resources/js/*.js').
    pipe(terser({
      format: {
        comments: false // 彻底移除所有注释
      }
    })).
    pipe(rename({suffix: '.min'})).
    pipe(gulp.dest('./src/main/resources/js/'))
}

function minUpload () {
  // concat js
  var jsJqueryUpload = [
    './src/main/resources/js/lib/jquery/file-upload/vendor/jquery.ui.widget.js',
    './src/main/resources/js/lib/jquery/file-upload/jquery.iframe-transport.js',
    './src/main/resources/js/lib/jquery/file-upload/jquery.fileupload.js',
    './src/main/resources/js/lib/jquery/file-upload/jquery.fileupload-process.js',
    './src/main/resources/js/lib/jquery/file-upload/jquery.fileupload-validate.js']
  return gulp.src(jsJqueryUpload).
    pipe(terser({
      format: {
        comments: false // 彻底移除所有注释
      }
    })).
    pipe(concat('jquery.fileupload.min.js')).
    pipe(gulp.dest('./src/main/resources/js/lib/jquery/file-upload/'))
}

function minLibs () {
  var jsCommonLib = [
    './src/main/resources/js/lib/jquery/jquery.min.js',
    './src/main/resources/js/lib/md5.js',
    './src/main/resources/js/lib/reconnecting-websocket.min.js',
    './src/main/resources/js/lib/jquery/jquery.bowknot.min.js',
    './src/main/resources/js/lib/ua-parser.min.js',
    './src/main/resources/js/lib/jquery/jquery.hotkeys.js',
    './src/main/resources/js/lib/jquery/jquery.pjax.js',
    './src/main/resources/js/lib/nprogress/nprogress.js']
  return gulp.src(jsCommonLib).
    pipe(terser({
      format: {
        comments: false // 彻底移除所有注释
      }
    })).
    pipe(concat('libs.min.js')).
    pipe(gulp.dest('./src/main/resources/js/lib/compress/'))
}

function minArticleLibs () {
  var jsArticleLib = [
    './src/main/resources/js/lib/sound-recorder/SoundRecorder.js',
    './src/main/resources/js/lib/jquery/jquery.qrcode.min.js',
    './src/main/resources/js/lib/aplayer/APlayer.min.js',
    './src/main/resources/js/lib/diff2html/diff2html.min.js',
    './src/main/resources/js/lib/diff2html/diff2html-ui.min.js',
    './src/main/resources/js/lib/diff2html/diff.min.js']
  return gulp.src(jsArticleLib).
    pipe(terser({
      format: {
        comments: false // 彻底移除所有注释
      }
    })).
    pipe(concat('article-libs.min.js')).
    pipe(gulp.dest('./src/main/resources/js/lib/compress/'))
}

gulp.task('default',
  gulp.series(cleanProcess, sassProcess,
    gulp.parallel(minJS, minUpload, minLibs),
    gulp.parallel(minArticleCSS, minArticleLibs)))
