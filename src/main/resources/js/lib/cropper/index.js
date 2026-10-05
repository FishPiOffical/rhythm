$(function () {
    var uploadedImageURL;

    // init
    $image.attr('src',originalImageURL).cropper(options);


    // rotate
    $rotate.on('click', function(){
        $image.cropper('rotate', 90);
    });

    // zoomOut
    $zoomOut.on('click',function(){
        $image.cropper('zoom', -0.1);
    });

    // zoomIn
    $zoomIn.on('click',function(){
        $image.cropper('zoom', 0.1);
    });

    // Move
    /*$move.on('click',function(){
     $image.cropper('setDragMode');
     });*/

    // reUpload
    $reUpload.on('click',function(){
        $image.cropper('destroy').attr('src', originalImageURL).cropper(options);
        if (uploadedImageURL) {
            URL.revokeObjectURL(uploadedImageURL);
            uploadedImageURL = '';
        }
    });

    // Keyboard
    $(document.body).on('keydown', function (e) {

        if (!$image.data('cropper') || this.scrollTop > 300) {
            return;
        }

        switch (e.which) {
            case 37:
                e.preventDefault();
                $image.cropper('move', -1, 0);
                break;

            case 38:
                e.preventDefault();
                $image.cropper('move', 0, -1);
                break;

            case 39:
                e.preventDefault();
                $image.cropper('move', 1, 0);
                break;

            case 40:
                e.preventDefault();
                $image.cropper('move', 0, 1);
                break;
        }

    });

    // save and upload cropped Img
    $save.on('click',function(){
        $('#image').cropper('getCroppedCanvas').toBlob(function (blob) {
            var formData = new FormData();
            // 必须显式给出文件名：裸 Blob 的 multipart 文件名是 "blob"（无扩展名），会被图床拒绝
            formData.append('file[]', blob, 'avatar.png');
            $.ajax({
                url: Label.servePath + '/upload',
                method: "POST",
                data: formData,
                processData: false,
                contentType: false,
                mimeType: "multipart/form-data",
                success: function (res) {
                    var data = typeof res === 'string' ? JSON.parse(res) : res;
                    var succMap = data && data.data ? data.data.succMap : null;
                    var keys = succMap ? Object.keys(succMap) : [];
                    if (keys.length === 0) {
                        Util.alert('头像上传失败' + (data && data.msg ? '：' + data.msg : '!'));
                        return;
                    }
                    var result = {
                        result: {
                            key: succMap[keys[0]]
                        }
                    };
                    updateAvatarByData(result);
                },
                error: function () {
                    Util.alert('头像上传失败!');
                }
            });
        }, 'image/png');
    })

    // Import Image
    var $inputImage = $('#inputImage');
    if (URL) {
        $inputImage.change(function () {
            var files = this.files;
            var file;

            if (!$image.data('cropper')) {
                return;
            }

            if (files && files.length) {
                file = files[0];

                if (/^image\/\w+$/.test(file.type)) {
                    if (uploadedImageURL) {
                        URL.revokeObjectURL(uploadedImageURL);
                    }

                    uploadedImageURL = URL.createObjectURL(file);
                    $image.cropper('destroy').attr('src', uploadedImageURL).cropper(options);
                    $inputImage.val('');
                } else {
                    alert('仅支持图片！')
                }
            }
        });
    } else {
        $inputImage.prop('disabled', true).parent().addClass('disabled');
    }
});
