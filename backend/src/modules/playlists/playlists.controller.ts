import { Body, Controller, Get, Param, Post } from '@nestjs/common';

@Controller()
export class PlaylistsController {
  @Get('playlists')
  getCustomerPlaylists() {
    return [];
  }

  @Get('admin/playlists')
  getAdminPlaylists() {
    return [];
  }

  @Post('admin/playlists')
  createPlaylist(@Body() body: unknown) {
    return { id: 'playlist-demo', ...(body as object) };
  }

  @Post('playlists/:id/refresh')
  refresh(@Param('id') id: string) {
    return { id, refreshed: true };
  }
}
